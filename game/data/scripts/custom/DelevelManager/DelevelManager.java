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
package custom.DelevelManager;

import com.l2journey.Config;
import com.l2journey.gameserver.data.xml.ExperienceData;
import com.l2journey.gameserver.model.actor.Npc;
import com.l2journey.gameserver.model.actor.Player;
import com.l2journey.gameserver.network.SystemMessageId;
import com.l2journey.gameserver.network.serverpackets.SystemMessage;

import ai.AbstractNpcAI;

/**
 * @author Mobius, KingHanker
 */
public class DelevelManager extends AbstractNpcAI
{
	private DelevelManager()
	{
		addStartNpc(Config.DELEVEL_MANAGER_NPCID);
		addTalkId(Config.DELEVEL_MANAGER_NPCID);
		addFirstTalkId(Config.DELEVEL_MANAGER_NPCID);
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		String htmltext = event;
		if (!Config.DELEVEL_MANAGER_ENABLED)
		{
			return htmltext;
		}
		
		switch (event)
		{
			case "delevel":
			{
				if (!(player.getLevel() > Config.DELEVEL_MANAGER_MINIMUM_DELEVEL))
				{
					return "1002000-2.htm";
				}
				
				if ((player.getLevel() > Config.DELEVEL_MANAGER_MINIMUM_DELEVEL) && (!(getQuestItemsCount(player, Config.DELEVEL_MANAGER_ITEMID) >= Config.DELEVEL_MANAGER_ITEMCOUNT)))
				{
					final SystemMessage sm = new SystemMessage(SystemMessageId.S2_UNIT_S_OF_THE_ITEM_S1_IS_ARE_REQUIRED);
					sm.addItemName(Config.DELEVEL_MANAGER_ITEMID);
					sm.addLong(Config.DELEVEL_MANAGER_ITEMCOUNT);
					player.sendPacket(sm);
					return "1002000-1.htm";
				}
				
				takeItems(player, Config.DELEVEL_MANAGER_ITEMID, Config.DELEVEL_MANAGER_ITEMCOUNT);
				player.getStat().removeExpAndSp((player.getExp() - ExperienceData.getInstance().getExpForLevel(player.getLevel() - 1)), 0);
				player.broadcastUserInfo();
				return "1002000.htm";
			}
		}
		
		return htmltext;
	}
	
	@Override
	public String onFirstTalk(Npc npc, Player player)
	{
		return "1002000.htm";
	}
	
	public static void main(String[] args)
	{
		new DelevelManager();
	}
}
