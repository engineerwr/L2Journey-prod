/*
 * Copyright (c) 2025 L2Journey Project
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ---
 *
 * Portions of this software are derived from the L2JMobius Project,
 * shared under the MIT License. The original license terms are preserved where
 * applicable..
 *
 */
package handlers.communityboard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import com.l2journey.Config;
import com.l2journey.EventsConfig;
import com.l2journey.commons.database.DatabaseFactory;
import com.l2journey.commons.threads.ThreadPool;
import com.l2journey.gameserver.cache.HtmCache;
import com.l2journey.gameserver.data.sql.ClanTable;
import com.l2journey.gameserver.data.xml.BuyListData;
import com.l2journey.gameserver.data.xml.ExperienceData;
import com.l2journey.gameserver.data.xml.MultisellData;
import com.l2journey.gameserver.data.xml.SkillData;
import com.l2journey.gameserver.handler.CommunityBoardHandler;
import com.l2journey.gameserver.handler.IParseBoardHandler;
import com.l2journey.gameserver.managers.PcCafePointsManager;
import com.l2journey.gameserver.managers.PremiumManager;
import com.l2journey.gameserver.model.actor.Creature;
import com.l2journey.gameserver.model.actor.Player;
import com.l2journey.gameserver.model.actor.Summon;
import com.l2journey.gameserver.model.item.enums.ItemProcessType;
import com.l2journey.gameserver.model.skill.Skill;
import com.l2journey.gameserver.model.zone.ZoneId;
import com.l2journey.gameserver.network.SystemMessageId;
import com.l2journey.gameserver.network.serverpackets.BuyList;
import com.l2journey.gameserver.network.serverpackets.ExBuySellList;
import com.l2journey.gameserver.network.serverpackets.ShowBoard;
import handlers.communityboard.SchemeBufferBoard;

/**
 * Home board.
 * @author Zoey76, Mobius, KingHanker
 */
public class HomeBoard implements IParseBoardHandler
{
	// SQL Queries
	private static final String COUNT_FAVORITES = "SELECT COUNT(*) AS favorites FROM `bbs_favorites` WHERE `playerId`=?";
	private final handlers.communityboard.TopBoard topBoard = new handlers.communityboard.TopBoard();
	private static final String NAVIGATION_PATH = "data/html/CommunityBoard/navigation.html";
	private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(SchemeBufferBoard.class.getName());

	private static final String[] COMMANDS =
	{
		"_bbshome",
		"_bbstop",
		"_bbspreset"
	};

	private static final String[] CUSTOM_COMMANDS =
	{
		Config.PREMIUM_SYSTEM_ENABLED && Config.COMMUNITY_PREMIUM_SYSTEM_ENABLED ? "_bbspremium" : null,
		Config.COMMUNITYBOARD_ENABLE_MULTISELLS ? "_bbsexcmultisell" : null,
		Config.COMMUNITYBOARD_ENABLE_MULTISELLS ? "_bbsmultisell" : null,
		Config.COMMUNITYBOARD_ENABLE_MULTISELLS ? "_bbssell" : null,
		Config.COMMUNITYBOARD_ENABLE_TELEPORTS ? "_bbsteleport" : null,
		Config.COMMUNITYBOARD_ENABLE_BUFFS ? "_bbsbuff" : null,
		Config.COMMUNITYBOARD_ENABLE_HEAL ? "_bbsheal" : null,
		Config.COMMUNITYBOARD_ENABLE_DELEVEL ? "_bbsdelevel" : null
	};

	private static final BiPredicate<String, Player> COMBAT_CHECK = (command, player) ->
	{
		boolean commandCheck = false;
		for (String c : CUSTOM_COMMANDS)
		{
			if ((c != null) && command.startsWith(c))
			{
				commandCheck = true;
				break;
			}
		}

		return commandCheck && (player.isCastingNow() || player.isCastingSimultaneouslyNow() || player.isInCombat() || player.isInDuel() || player.isInOlympiadMode() || player.isInsideZone(ZoneId.SIEGE) || player.isInsideZone(ZoneId.PVP) || (player.getPvpFlag() > 0) || player.isAlikeDead() || player.isOnEvent() || player.isInStoreMode());
	};

	private static final Predicate<Player> KARMA_CHECK = player -> Config.COMMUNITYBOARD_KARMA_DISABLED && (player.getKarma() > 0);

	private static final Map<Integer, String> BOSS_STATUS_CACHE = new ConcurrentHashMap<>();
	private static final int[] BOSSES =
	{
		29068,
		29020,
		29118,
		29006,
		29014,
		29001,
		29028
	};

	private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private static LocalDateTime lastUpdateTime = LocalDateTime.now();
	private static LocalDateTime nextUpdateTime = lastUpdateTime.plusHours(1);
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	static
	{
		// Updates the cache immediately on startup
		updateBossStatusCache();
		// Schedules update every 1 hour
		scheduler.scheduleAtFixedRate(HomeBoard::updateBossStatusCache, 1, 1, TimeUnit.HOURS);
	}

	private static void updateBossStatusCache()
	{
		for (int bossId : BOSSES)
		{
			long delay = com.l2journey.gameserver.managers.GrandBossManager.getInstance().getStatSet(bossId).getLong("respawn_time");
			if (delay <= System.currentTimeMillis())
			{
				BOSS_STATUS_CACHE.put(bossId, "<font color=\"32C332\">Is Alive</font>");
			}
			else
			{
				BOSS_STATUS_CACHE.put(bossId, "<font color=\"FF3333\">Is Dead</font>");
			}
		}

		lastUpdateTime = LocalDateTime.now();
		nextUpdateTime = lastUpdateTime.plusHours(1);
	}

	/**
	 * Returns the last update time for the boss status cache.
	 * @return String formatted as HH:mm
	 */
	public static String getLastUpdateTime()
	{
		return lastUpdateTime.format(TIME_FORMAT);
	}

	/**
	 * Returns the next scheduled update time for the boss status cache.
	 * @return String formatted as HH:mm
	 */
	public static String getNextUpdateTime()
	{
		return nextUpdateTime.format(TIME_FORMAT);
	}

	/**
	 * Returns the list of supported Community Board commands.
	 * @return Array of command strings
	 */
	@Override
	public String[] getCommunityBoardCommands()
	{
		final List<String> commands = new ArrayList<>();
		commands.addAll(Arrays.asList(COMMANDS));
		commands.addAll(Arrays.asList(CUSTOM_COMMANDS));
		commands.addAll(Arrays.asList(topBoard.getCommunityBoardCommands()));

		return commands.stream().filter(Objects::nonNull).toArray(String[]::new);
	}

	/**
	 * Parses and executes a Community Board command for the given player.
	 * @param command The command string
	 * @param player The player instance
	 * @return true if the command was handled, false otherwise
	 */
	@Override
	public boolean parseCommunityBoardCommand(String command, Player player)
	{
		if (!Config.COMMUNITYBOARD_ENABLED)
		{
			player.sendPacket(SystemMessageId.THE_COMMUNITY_SERVER_IS_CURRENTLY_OFFLINE);
			return false;
		}
		// Old custom conditions check move to here
		if (Config.COMMUNITYBOARD_COMBAT_DISABLED && COMBAT_CHECK.test(command, player))
		{
			player.sendMessage("You can't use the Community Board right now.");
			return false;
		}

		if (KARMA_CHECK.test(player))
		{
			player.sendMessage("Players with Karma cannot use the Community Board.");
			return false;
		}

		if (Config.COMMUNITYBOARD_PEACE_ONLY && !player.isInsideZone(ZoneId.PEACE))
		{
			player.sendMessage("Community Board cannot be used out of peace zone.");
			return false;
		}

		String returnHtml = null;
		String navigation = null;

		if (Config.COMMUNITYBOARD_ENABLED)
		{
			navigation = HtmCache.getInstance().getHtm(player, NAVIGATION_PATH);
		}

		if (command.equals("_bbshome") || command.equals("_bbstop"))
		{
			final String customPath = Config.COMMUNITYBOARD_ENABLED ? "Custom/" : "";
			CommunityBoardHandler.getInstance().addBypass(player, "Home", command);
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/" + customPath + "home/home.html");

			if (!Config.COMMUNITYBOARD_ENABLED)
			{
				returnHtml = returnHtml.replace("%fav_count%", Integer.toString(getFavoriteCount(player)));
				returnHtml = returnHtml.replace("%region_count%", Integer.toString(getRegionCount(player)));
				returnHtml = returnHtml.replace("%clan_count%", Integer.toString(ClanTable.getInstance().getClanCount()));
			}
		}
		else if (command.startsWith("_bbstopboard"))
		{
			return topBoard.parseCommunityBoardCommand(command, player);
		}
		else if (command.startsWith("_bbstop;"))
		{
			final String customPath = Config.COMMUNITYBOARD_ENABLED ? "Custom/" : "";
			final String path = command.replace("_bbstop;", "");
			if (path.endsWith(".html"))
			{
				returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/" + customPath + path);

				// Special handling for buffer main page to show player schemes
				if (path.equals("buffer/main.html"))
				{
					returnHtml = returnHtml.replace("%schemes%", SchemeBufferBoard.generateSchemesHtml(player));
				}
			}
		}
		else if (command.startsWith("_bbsmultisell"))
		{
			final String fullBypass = command.replace("_bbsmultisell;", "");
			final String[] buypassOptions = fullBypass.split(",");
			final int multisellId = Integer.parseInt(buypassOptions[0]);
			final String page = buypassOptions[1];
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/" + page + ".html");
			MultisellData.getInstance().separateAndSend(multisellId, player, null, false);
		}
		else if (command.startsWith("_bbsexcmultisell"))
		{
			final String fullBypass = command.replace("_bbsexcmultisell;", "");
			final String[] buypassOptions = fullBypass.split(",");
			final int multisellId = Integer.parseInt(buypassOptions[0]);
			final String page = buypassOptions[1];
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/" + page + ".html");
			MultisellData.getInstance().separateAndSend(multisellId, player, null, true);
		}
		else if (command.startsWith("_bbssell"))
		{
			final String page = command.replace("_bbssell;", "");
			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/" + page + ".html");
			player.sendPacket(new BuyList(BuyListData.getInstance().getBuyList(423), player.getAdena(), 0));
			player.sendPacket(new ExBuySellList(player, false));
		}
		else if (command.startsWith("_bbsteleport"))
		{
			final String teleBuypass = command.replace("_bbsteleport;", "");
			if (player.getInventory().getInventoryItemCount(Config.COMMUNITYBOARD_CURRENCY, -1) < Config.COMMUNITYBOARD_TELEPORT_PRICE)
			{
				player.sendMessage("Not enough currency!");
			}
			else if (Config.COMMUNITY_AVAILABLE_TELEPORTS.get(teleBuypass) != null)
			{
				player.disableAllSkills();
				player.sendPacket(new ShowBoard());
				player.destroyItemByItemId(ItemProcessType.FEE, Config.COMMUNITYBOARD_CURRENCY, Config.COMMUNITYBOARD_TELEPORT_PRICE, player, true);
				player.setIn7sDungeon(false);
				player.setInstanceId(0);
				player.teleToLocation(Config.COMMUNITY_AVAILABLE_TELEPORTS.get(teleBuypass), 0);
				ThreadPool.schedule(player::enableAllSkills, 3000);
			}
		}
		else if (command.startsWith("_bbsbuff"))
		{
			final String fullBypass = command.replace("_bbsbuff;", "");

			final String[] buypassOptions = fullBypass.split(";");
			final int buffCount = buypassOptions.length - 1;
			final String page = buypassOptions[buffCount];

			if (player.getInventory().getInventoryItemCount(Config.COMMUNITYBOARD_CURRENCY, -1) < ((long) Config.COMMUNITYBOARD_BUFF_PRICE * buffCount))
			{
				player.sendMessage("Not enough currency!");
			}
			else
			{
				player.destroyItemByItemId(ItemProcessType.FEE, Config.COMMUNITYBOARD_CURRENCY, (long) Config.COMMUNITYBOARD_BUFF_PRICE * buffCount, player, true);

				final Summon pet = player.getSummon();
				final List<Creature> targets = new ArrayList<>(4);
				targets.add(player);

				if (pet != null)
				{
					targets.add(pet);
				}

				for (int i = 0; i < buffCount; i++)
				{
					final Skill skill = SkillData.getInstance().getSkill(Integer.parseInt(buypassOptions[i].split(",")[0]), Integer.parseInt(buypassOptions[i].split(",")[1]));

					if (!Config.COMMUNITY_AVAILABLE_BUFFS.contains(skill.getId()))
					{
						continue;
					}

					for (Creature target : targets)
					{
						skill.applyEffects(player, target);
					}
				}
			}

			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/" + page + ".html");
		}
		else if (command.startsWith("_bbspreset")) 
		{
			final String fullBypass = command.replace("_bbspreset;", "");

			String buffPreset = BuffPresets.getBuffPreset(fullBypass);
			final String[] buypassOptions = buffPreset.split(";");
			final int buffCount = buypassOptions.length - 1;

			if (player.getInventory().getInventoryItemCount(Config.COMMUNITYBOARD_CURRENCY, -1) < ((long) Config.COMMUNITYBOARD_BUFF_PRICE * buffCount))
			{
				player.sendMessage("Not enough currency!");
			}
			else
			{
				player.destroyItemByItemId(ItemProcessType.FEE, Config.COMMUNITYBOARD_CURRENCY, (long) Config.COMMUNITYBOARD_BUFF_PRICE * buffCount, player, true);

				final Summon pet = player.getSummon();
				final List<Creature> targets = new ArrayList<>(4);
				targets.add(player);

				if (pet != null)
				{
					targets.add(pet);
				}

				for (int i = 0; i < buffCount; i++)
				{
					final Skill skill = SkillData.getInstance().getSkill(Integer.parseInt(buypassOptions[i].split(",")[0]), Integer.parseInt(buypassOptions[i].split(",")[1]));

					if (!Config.COMMUNITY_AVAILABLE_BUFFS.contains(skill.getId()))
					{
						continue;
					}

					for (Creature target : targets)
					{
						skill.applyEffects(player, target);
					}
				}
			}

			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/buffer/main.html");
			returnHtml = returnHtml.replace("%schemes%", SchemeBufferBoard.generateSchemesHtml(player));
		}
		else if (command.startsWith("_bbsheal"))
		{
			final String page = command.replace("_bbsheal;", "");
			if (player.getInventory().getInventoryItemCount(Config.COMMUNITYBOARD_CURRENCY, -1) < (Config.COMMUNITYBOARD_HEAL_PRICE))
			{
				player.sendMessage("Not enough currency!");
			}
			else
			{
				player.destroyItemByItemId(ItemProcessType.FEE, Config.COMMUNITYBOARD_CURRENCY, Config.COMMUNITYBOARD_HEAL_PRICE, player, true);
				player.setCurrentHp(player.getMaxHp());
				player.setCurrentMp(player.getMaxMp());
				player.setCurrentCp(player.getMaxCp());

				if (player.hasSummon())
				{
					player.getSummon().setCurrentHp(player.getSummon().getMaxHp());
					player.getSummon().setCurrentMp(player.getSummon().getMaxMp());
					player.getSummon().setCurrentCp(player.getSummon().getMaxCp());
				}

				player.updateUserInfo();
				player.sendMessage("You used heal!");
			}

			returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/" + page + ".html");
		}
		else if (command.equals("_bbsdelevel"))
		{
			if (player.getInventory().getInventoryItemCount(Config.COMMUNITYBOARD_CURRENCY, -1) < Config.COMMUNITYBOARD_DELEVEL_PRICE)
			{
				player.sendMessage("Not enough currency!");
			}
			else if (player.getLevel() == 1)
			{
				player.sendMessage("You are at minimum level!");
			}
			else
			{
				player.destroyItemByItemId(ItemProcessType.FEE, Config.COMMUNITYBOARD_CURRENCY, Config.COMMUNITYBOARD_DELEVEL_PRICE, player, true);
				final int newLevel = player.getLevel() - 1;
				player.setExp(ExperienceData.getInstance().getExpForLevel(newLevel));
				player.getStat().setLevel((byte) newLevel);
				player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp());
				player.setCurrentCp(player.getMaxCp());
				player.broadcastUserInfo();
				player.checkPlayerSkills(); // Adjust skills according to new level.
				returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/delevel/complete.html");
			}
		}
		else if (command.startsWith("_bbspremium"))
		{
			final String fullBypass = command.replace("_bbspremium;", "");
			final String[] buypassOptions = fullBypass.split(",");
			final int premiumDays = Integer.parseInt(buypassOptions[0]);
			if ((premiumDays < 1) || (premiumDays > 30) || (player.getInventory().getInventoryItemCount(Config.COMMUNITY_PREMIUM_COIN_ID, -1) < (Config.COMMUNITY_PREMIUM_PRICE_PER_DAY * premiumDays)))
			{
				player.sendMessage("Not enough currency!");
			}
			else
			{
				player.destroyItemByItemId(ItemProcessType.FEE, Config.COMMUNITY_PREMIUM_COIN_ID, Config.COMMUNITY_PREMIUM_PRICE_PER_DAY * premiumDays, player, true);
				PremiumManager.getInstance().addPremiumTime(player.getAccountName(), premiumDays, TimeUnit.DAYS);
				player.sendMessage("Your account will now have premium status until " + new SimpleDateFormat("dd.MM.yyyy HH:mm").format(PremiumManager.getInstance().getPremiumExpiration(player.getAccountName())) + ".");
				if (EventsConfig.PC_CAFE_RETAIL_LIKE)
				{
					PcCafePointsManager.getInstance().run(player);
				}

				returnHtml = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/premium/thankyou.html");
			}
		}

		if (returnHtml != null)
		{
			if (Config.COMMUNITYBOARD_ENABLED)
			{
				assert navigation != null;
				returnHtml = returnHtml.replace("%navigation%", navigation);
			}

			CommunityBoardHandler.separateAndSend(returnHtml, player);
		}

		return false;
	}

	/**
	 * Gets the Favorite links for the given player.
	 * @param player the player
	 * @return the favorite links count
	 */
	private static int getFavoriteCount(Player player)
	{
		int count = 0;
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(COUNT_FAVORITES))
		{
			ps.setInt(1, player.getObjectId());
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					count = rs.getInt("favorites");
				}
			}
		}
		catch (Exception e)
		{
			LOG.warning(FavoriteBoard.class.getSimpleName() + ": Couldn't load favorites count for " + player);
		}

		return count;
	}

	/**
	 * Gets the registered regions count for the given player.
	 * @param player the player
	 * @return the registered regions count
	 */
	private static int getRegionCount(Player player)
	{
		return 0; // TODO: Implement.
	}

	/**
	 * Returns the status of the boss for Community Board HTML.
	 * @param bossId Boss NPC ID
	 * @return HTML string: 'Is Alive' (green) or 'Is Dead' (red)
	 */
	public static String getBossStatus(int bossId)
	{
		return BOSS_STATUS_CACHE.getOrDefault(bossId, "?");
	}
}
