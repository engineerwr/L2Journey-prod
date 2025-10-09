/*
 * Copyright (c) 2015 L2Journey Project
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package handlers.communityboard;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import com.l2journey.Config;
import com.l2journey.gameserver.cache.HtmCache;
import com.l2journey.gameserver.data.SchemeBufferTable;
import com.l2journey.gameserver.data.xml.SkillData;
import com.l2journey.gameserver.handler.CommunityBoardHandler;
import com.l2journey.gameserver.handler.IParseBoardHandler;
import com.l2journey.gameserver.model.actor.Creature;
import com.l2journey.gameserver.model.actor.Player;
import com.l2journey.gameserver.model.actor.Summon;
import com.l2journey.gameserver.model.item.enums.ItemProcessType;
import com.l2journey.gameserver.model.skill.Skill;
import com.l2journey.gameserver.util.HtmlUtil;

/**
 * Scheme Buffer Community Board handler.
 * @author Wesley
 */
public class SchemeBufferBoard implements IParseBoardHandler
{
	private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(SchemeBufferBoard.class.getName());
	private static final int PAGE_LIMIT = 6;
	private static final String NAVIGATION_PATH = "data/html/CommunityBoard/navigation.html";

	private static final String[] COMMANDS =
		{
			"_scheme",
			"_scheme_menu",
			"_scheme_cleanup",
			"_scheme_heal",
			"_scheme_support",
			"_scheme_givebuffs",
			"_scheme_editschemes",
			"_scheme_skill",
			"_scheme_skillselect",
			"_scheme_skillunselect",
			"_scheme_createscheme",
			"_scheme_deletescheme"
		};

	public SchemeBufferBoard()
	{
		LOGGER.info("SchemeBufferBoard initialized and registered with commands: " + java.util.Arrays.toString(COMMANDS));
	}

	private void showMainMenu(Player player)
	{
		String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/buffer/index.html");

		if (Config.COMMUNITYBOARD_ENABLED)
		{
			final String navigation = HtmCache.getInstance().getHtm(player, NAVIGATION_PATH);
			html = html.replace("%navigation%", navigation);
		}

		CommunityBoardHandler.separateAndSend(html, player);
	}

	private void showBufferMainPage(Player player)
	{
		String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/buffer/main.html");

		// Replace schemes placeholder
		html = html.replace("%schemes%", generateSchemesHtml(player));

		if (Config.COMMUNITYBOARD_ENABLED)
		{
			final String navigation = HtmCache.getInstance().getHtm(player, NAVIGATION_PATH);
			html = html.replace("%navigation%", navigation);
		}

		CommunityBoardHandler.separateAndSend(html, player);
	}

	private void showGiveBuffsWindow(Player player)
	{
		final StringBuilder sb = new StringBuilder(200);
		final Map<String, List<Integer>> schemes = SchemeBufferTable.getInstance().getPlayerSchemes(player.getObjectId());

		if ((schemes == null) || schemes.isEmpty())
		{
			sb.append("<font color=\"LEVEL\">You haven't defined any scheme.</font>");
		}
		else
		{
			for (Entry<String, List<Integer>> scheme : schemes.entrySet())
			{
				final int cost = getFee(scheme.getValue());
				sb.append("<font color=\"LEVEL\">" + scheme.getKey() + " [" + scheme.getValue().size() + " skill(s)]" + ((cost > 0) ? " - cost: " + NumberFormat.getInstance(Locale.ENGLISH).format(cost) : "") + "</font><br1>");
				sb.append("<a action=\"bypass _scheme_givebuffs;" + scheme.getKey() + ";" + cost + "\">Use on Me</a>&nbsp;|&nbsp;");
				sb.append("<a action=\"bypass _scheme_givebuffs;" + scheme.getKey() + ";" + cost + ";pet\">Use on Pet</a>&nbsp;|&nbsp;");
				sb.append("<a action=\"bypass _scheme_editschemes;Buffs;" + scheme.getKey() + ";1\">Edit</a>&nbsp;|&nbsp;");
				sb.append("<a action=\"bypass _scheme_deletescheme;" + scheme.getKey() + "\">Delete</a><br>");
			}
		}

		String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/buffer/schemes.html");
		html = html.replace("%schemes%", sb.toString());
		html = html.replace("%max_schemes%", String.valueOf(Config.BUFFER_MAX_SCHEMES));

		if (Config.COMMUNITYBOARD_ENABLED)
		{
			final String navigation = HtmCache.getInstance().getHtm(player, NAVIGATION_PATH);
			html = html.replace("%navigation%", navigation);
		}

		CommunityBoardHandler.separateAndSend(html, player);
	}

	private void showEditSchemeWindow(Player player, String groupType, String schemeName, int page)
	{
		final List<Integer> schemeSkills = SchemeBufferTable.getInstance().getScheme(player.getObjectId(), schemeName);

		String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/buffer/edit.html");
		html = html.replace("%schemename%", schemeName);
		html = html.replace("%count%", getCountOf(schemeSkills, false) + " / " + player.getStat().getMaxBuffCount() + " buffs, " + getCountOf(schemeSkills, true) + " / " + Config.DANCES_MAX_AMOUNT + " dances/songs");
		html = html.replace("%typesframe%", getTypesFrame(groupType, schemeName));
		html = html.replace("%skilllistframe%", getGroupSkillList(player, groupType, schemeName, page));

		if (Config.COMMUNITYBOARD_ENABLED)
		{
			final String navigation = HtmCache.getInstance().getHtm(player, NAVIGATION_PATH);
			html = html.replace("%navigation%", navigation);
		}

		CommunityBoardHandler.separateAndSend(html, player);
	}

	private String getGroupSkillList(Player player, String groupType, String schemeName, int pageValue)
	{
		List<Integer> skills = SchemeBufferTable.getInstance().getSkillsIdsByType(groupType);
		if (skills.isEmpty())
		{
			return "That group doesn't contain any skills.";
		}

		final int max = HtmlUtil.countPageNumber(skills.size(), PAGE_LIMIT);
		int page = pageValue;
		if (page > max)
		{
			page = max;
		}

		skills = skills.subList((page - 1) * PAGE_LIMIT, Math.min(page * PAGE_LIMIT, skills.size()));

		final List<Integer> schemeSkills = SchemeBufferTable.getInstance().getScheme(player.getObjectId(), schemeName);
		final StringBuilder sb = new StringBuilder(skills.size() * 150);
		int row = 0;
		for (int skillId : skills)
		{
			sb.append(((row % 2) == 0 ? "<table width=\"280\" bgcolor=\"000000\"><tr>" : "<table width=\"280\"><tr>"));

			final Skill skill = SkillData.getInstance().getSkill(skillId, 1);
			if (schemeSkills.contains(skillId))
			{
				sb.append("<td height=40 width=40><img src=\"" + skill.getIcon() + "\" width=32 height=32></td><td width=190>" + skill.getName() + "<br1><font color=\"B09878\">" + SchemeBufferTable.getInstance().getAvailableBuff(skillId).getDescription() + "</font></td><td><button action=\"bypass _scheme_skillunselect;" + groupType + ";" + schemeName + ";" + skillId + ";" + page + "\" width=32 height=32 back=\"L2UI_CH3.mapbutton_zoomout2\" fore=\"L2UI_CH3.mapbutton_zoomout1\"></td>");
			}
			else
			{
				sb.append("<td height=40 width=40><img src=\"" + skill.getIcon() + "\" width=32 height=32></td><td width=190>" + skill.getName() + "<br1><font color=\"B09878\">" + SchemeBufferTable.getInstance().getAvailableBuff(skillId).getDescription() + "</font></td><td><button action=\"bypass _scheme_skillselect;" + groupType + ";" + schemeName + ";" + skillId + ";" + page + "\" width=32 height=32 back=\"L2UI_CH3.mapbutton_zoomin2\" fore=\"L2UI_CH3.mapbutton_zoomin1\"></td>");
			}

			sb.append("</tr></table><img src=\"L2UI.SquareGray\" width=277 height=1>");
			row++;
		}

		sb.append("<br><img src=\"L2UI.SquareGray\" width=277 height=1><table width=\"100%\" bgcolor=000000><tr>");
		if (page > 1)
		{
			sb.append("<td align=left width=70><a action=\"bypass _scheme_editschemes;" + groupType + ";" + schemeName + ";" + (page - 1) + "\">Previous</a></td>");
		}
		else
		{
			sb.append("<td align=left width=70>Previous</td>");
		}

		sb.append("<td align=center width=100>Page " + page + "</td>");
		if (page < max)
		{
			sb.append("<td align=right width=70><a action=\"bypass _scheme_editschemes;" + groupType + ";" + schemeName + ";" + (page + 1) + "\">Next</a></td>");
		}
		else
		{
			sb.append("<td align=right width=70>Next</td>");
		}

		sb.append("</tr></table><img src=\"L2UI.SquareGray\" width=277 height=1>");
		return sb.toString();
	}

	private static String getTypesFrame(String groupType, String schemeName)
	{
		final StringBuilder sb = new StringBuilder(500);
		sb.append("<table>");

		int count = 0;
		for (String type : SchemeBufferTable.getInstance().getSkillTypes())
		{
			if (count == 0)
			{
				sb.append("<tr>");
			}

			if (groupType.equalsIgnoreCase(type))
			{
				sb.append("<td width=65>" + type + "</td>");
			}
			else
			{
				sb.append("<td width=65><a action=\"bypass _scheme_editschemes;" + type + ";" + schemeName + ";1\">" + type + "</a></td>");
			}

			count++;
			if (count == 4)
			{
				sb.append("</tr>");
				count = 0;
			}
		}

		if (!sb.toString().endsWith("</tr>"))
		{
			sb.append("</tr>");
		}

		sb.append("</table>");

		return sb.toString();
	}

	private static int getFee(List<Integer> list)
	{
		if (Config.BUFFER_STATIC_BUFF_COST > 0)
		{
			return list.size() * Config.BUFFER_STATIC_BUFF_COST;
		}

		int fee = 0;
		for (int sk : list)
		{
			fee += SchemeBufferTable.getInstance().getAvailableBuff(sk).getPrice();
		}

		return fee;
	}

	private static int getCountOf(List<Integer> skills, boolean dances)
	{
		int count = 0;
		for (int skillId : skills)
		{
			if (SkillData.getInstance().getSkill(skillId, 1).isDance() == dances)
			{
				count++;
			}
		}

		return count;
	}

	/**
	 * Generate player schemes HTML for display in main buffer page
	 * @param player the player
	 * @return HTML string with player schemes or empty message
	 */
	public static String generateSchemesHtml(Player player)
	{
		final StringBuilder sb = new StringBuilder(200);
		final Map<String, List<Integer>> schemes = SchemeBufferTable.getInstance().getPlayerSchemes(player.getObjectId());

		if ((schemes == null) || schemes.isEmpty())
		{
			sb.append("<font color=\"808080\">No schemes created yet. Click 'Manage' to create one.</font>");
		}
		else
		{
			for (Entry<String, List<Integer>> scheme : schemes.entrySet())
			{
				final int cost = getFee(scheme.getValue());
				sb.append("<table width=500><tr>");
				sb.append("<td width=200><font color=\"LEVEL\">" + scheme.getKey() + "</font></td>");
				sb.append("<td width=100><font color=\"B09878\">" + scheme.getValue().size() + " buff(s)</font></td>");
				sb.append("<td width=100><button value=\"Use on Me\" action=\"bypass _scheme_givebuffs;" + scheme.getKey() + ";" + cost + ";main\" width=90 height=22 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></td>");
				sb.append("<td width=100><button value=\"Use on Pet\" action=\"bypass _scheme_givebuffs;" + scheme.getKey() + ";" + cost + ";pet;main\" width=90 height=22 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></td>");
				sb.append("</tr></table>");
			}
		}

		return sb.toString();
	}

	/**
	 * Parses a community board command.
	 *
	 * @param command the command
	 * @param player  the player
	 * @return
	 */
	@Override
	public boolean parseCommunityBoardCommand(String command, Player player) {
		LOGGER.info("SchemeBufferBoard.parseCommunityBoardCommand called with command: " + command);

		// Replace space with semicolon for createscheme command
		final String processedCommand = command.replace("_scheme_createscheme ", "_scheme_createscheme;");

		final StringTokenizer st = new StringTokenizer(processedCommand.replace(command.split(";")[0] + ";", ""), ";");
		final String currentCommand = processedCommand.split(";")[0];

		if (currentCommand.equals("_scheme") || currentCommand.equals("_scheme_menu"))
		{
			showMainMenu(player);
		}
		else if (currentCommand.equals("_scheme_cleanup"))
		{
			player.stopAllEffects();

			final Summon summon = player.getSummon();
			if (summon != null)
			{
				summon.stopAllEffects();
			}

			// Check if should return to main page
			if (st.hasMoreTokens())
			{
				String nextToken = st.nextToken();
				if (nextToken.contains("main"))
				{
					showBufferMainPage(player);
				}
				else
				{
					showMainMenu(player);
				}
			}
			else
			{
				showMainMenu(player);
			}
		}
		else if (currentCommand.equals("_scheme_heal"))
		{
			player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp());
			player.setCurrentCp(player.getMaxCp());

			final Summon summon = player.getSummon();
			if (summon != null)
			{
				summon.setCurrentHpMp(summon.getMaxHp(), summon.getMaxMp());
			}

			// Check if should return to main page
			if (st.hasMoreTokens())
			{
				String nextToken = st.nextToken();
				if (nextToken.contains("main"))
				{
					showBufferMainPage(player);
				}
				else
				{
					showMainMenu(player);
				}
			}
			else
			{
				showMainMenu(player);
			}
		}
		else if (currentCommand.equals("_scheme_support"))
		{
			showGiveBuffsWindow(player);
		}
		else if (currentCommand.equals("_scheme_givebuffs"))
		{
			final String schemeName = st.nextToken();
			final int cost = Integer.parseInt(st.nextToken());
			String targetType = null;
			String returnPage = null;

			// Parse optional parameters (target type and return page)
			if (st.hasMoreTokens())
			{
				final String nextToken = st.nextToken();
				if ((nextToken != null) && nextToken.equalsIgnoreCase("pet"))
				{
					targetType = "pet";
					if (st.hasMoreTokens())
					{
						returnPage = st.nextToken();
					}
				}
				else if ((nextToken != null) && nextToken.equalsIgnoreCase("main"))
				{
					returnPage = "main";
				}
			}

			Creature target = ((targetType != null) && targetType.equalsIgnoreCase("pet")) ? player.getSummon() : player;

			if (target == null)
			{
				player.sendMessage("You don't have a pet.");
			}
			else if ((cost == 0) || ((Config.BUFFER_ITEM_ID == 57) && player.reduceAdena(ItemProcessType.FEE, cost, null, true)) || ((Config.BUFFER_ITEM_ID != 57) && player.destroyItemByItemId(ItemProcessType.FEE, Config.BUFFER_ITEM_ID, cost, player, true)))
			{
				for (int skillId : SchemeBufferTable.getInstance().getScheme(player.getObjectId(), schemeName))
				{
					SkillData.getInstance().getSkill(skillId, SchemeBufferTable.getInstance().getAvailableBuff(skillId).getLevel()).applyEffects(player, target);
				}
			}

			// Return to appropriate page
			if ((returnPage != null) && returnPage.equalsIgnoreCase("main"))
			{
				showBufferMainPage(player);
			}
			else
			{
				showGiveBuffsWindow(player);
			}
		}
		else if (currentCommand.equals("_scheme_editschemes"))
		{
			showEditSchemeWindow(player, st.nextToken(), st.nextToken(), Integer.parseInt(st.nextToken()));
		}
		else if (currentCommand.startsWith("_scheme_skill"))
		{
			final String groupType = st.nextToken();
			final String schemeName = st.nextToken();
			final int skillId = Integer.parseInt(st.nextToken());
			final int page = Integer.parseInt(st.nextToken());
			final List<Integer> skills = SchemeBufferTable.getInstance().getScheme(player.getObjectId(), schemeName);

			if (currentCommand.equals("_scheme_skillselect") && !schemeName.equalsIgnoreCase("none"))
			{
				final Skill skill = SkillData.getInstance().getSkill(skillId, SkillData.getInstance().getMaxLevel(skillId));
				if (skill.isDance())
				{
					if (getCountOf(skills, true) < Config.DANCES_MAX_AMOUNT)
					{
						skills.add(skillId);
					}
					else
					{
						player.sendMessage("This scheme has reached the maximum amount of dances/songs.");
					}
				}
				else
				{
					if (getCountOf(skills, false) < player.getStat().getMaxBuffCount())
					{
						skills.add(skillId);
					}
					else
					{
						player.sendMessage("This scheme has reached the maximum amount of buffs.");
					}
				}
			}
			else if (currentCommand.equals("_scheme_skillunselect"))
			{
				skills.remove(Integer.valueOf(skillId));
			}

			showEditSchemeWindow(player, groupType, schemeName, page);
		}
		else if (currentCommand.equals("_scheme_createscheme"))
		{
			// Auto-generate scheme name
			final Map<String, List<Integer>> schemes = SchemeBufferTable.getInstance().getPlayerSchemes(player.getObjectId());

			if ((schemes != null) && (schemes.size() >= Config.BUFFER_MAX_SCHEMES))
			{
				player.sendMessage("Maximum schemes amount is already reached.");
				showGiveBuffsWindow(player);
				return false;
			}

			// Find next available scheme number
			int schemeNumber = 1;
			String schemeName = "Scheme " + schemeNumber;

			if (schemes != null)
			{
				while (schemes.containsKey(schemeName))
				{
					schemeNumber++;
					schemeName = "Scheme " + schemeNumber;
				}
			}

			SchemeBufferTable.getInstance().setScheme(player.getObjectId(), schemeName, new ArrayList<>());
			player.sendMessage("Created: " + schemeName);
			showGiveBuffsWindow(player);
		}
		else if (currentCommand.equals("_scheme_deletescheme"))
		{
			try
			{
				final String schemeName = st.nextToken();
				final Map<String, List<Integer>> schemes = SchemeBufferTable.getInstance().getPlayerSchemes(player.getObjectId());
				if ((schemes != null) && schemes.containsKey(schemeName))
				{
					schemes.remove(schemeName);
				}
			}
			catch (Exception e)
			{
				player.sendMessage("This scheme name is invalid.");
			}

			showGiveBuffsWindow(player);
		}

		return true;
	}

	/**
	 * Gets the community board commands.
	 *
	 * @return the community board commands
	 */
	@Override
	public String[] getCommunityBoardCommands() {
		return COMMANDS;
	}
}
