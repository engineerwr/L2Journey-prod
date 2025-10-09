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
package ai.areas.PaganTemple.AndreasVanHalter;

import com.l2journey.gameserver.data.xml.DoorData;
import com.l2journey.gameserver.model.Location;
import com.l2journey.gameserver.model.actor.Npc;
import com.l2journey.gameserver.model.actor.Player;

import ai.AbstractNpcAI;

/**
 * @author KingHanker, Zoinha
 */
public class AndreasVanHalter extends AbstractNpcAI
{
	private static final int ANDREAS_VAN_HALTER = 29062;
	
	private static final int TRIOLS_REVELATIONS1 = 32058;
	private static final int TRIOLS_REVELATIONS2 = 32059;
	private static final int TRIOLS_REVELATIONS3 = 32060;
	private static final int TRIOLS_REVELATIONS4 = 32061;
	private static final int TRIOLS_REVELATIONS5 = 32062;
	private static final int TRIOLS_REVELATIONS6 = 32063;
	// private static final int TRIOLS_REVELATIONS7 = 32064; //TODO
	// private static final int TRIOLS_REVELATIONS8 = 32065;
	// private static final int TRIOLS_REVELATIONS9 = 32066;
	// private static final int TRIOLS_REVELATIONS10 = 32067;
	// private static final int TRIOLS_REVELATIONS11 = 32068;
	// private static final int RITUAL_OFFERING = 32038;
	// private static final int ALTAR_GATEKEEPER = 32051;
	// private static final int ANDREAS_CAPTAIN_ROYAL_GUARD1 = 22175;
	// private static final int ANDREAS_CAPTAIN_ROYAL_GUARD2 = 22188;
	// private static final int ANDREAS_CAPTAIN_ROYAL_GUARD3 = 22191;
	// private static final int ANDREAS_ROYAL_GUARD1 = 22192;
	// private static final int ANDREAS_ROYAL_GUARD2 = 22193;
	// private static final int ANDREAS_ROYAL_GUARD3 = 22176;
	
	private static final int ALTAR_SECRET_DOOR_1 = 19160013;
	private static final int ALTAR_SECRET_DOOR_2 = 19160012;
	private static final int ALTAR_FLAME_GATE_1 = 19160017;
	private static final int ALTAR_FLAME_GATE_2 = 19160016;
	private static final int ALTAR_INNER_GATE_1 = 19160015;
	private static final int ALTAR_INNER_GATE_2 = 19160014;
	
	private static final String CLOSE_SECRET_DOOR_1_EVENT = "Close_Door1";
	private static final String CLOSE_SECRET_DOOR_2_EVENT = "Close_Door2";
	private static final String NO_KEY_HTML = "noKey.htm";
	private static final String DOOR_OPEN_HTML = "doorOpen.htm";
	
	private static final int CHAPEL_KEY = 8274;
	private static final int KEY_OF_DARKNESS = 8275;
	
	// Triol's Revelation.
	private static final int[] NPCS =
	{
		TRIOLS_REVELATIONS1,
		TRIOLS_REVELATIONS2,
		TRIOLS_REVELATIONS3,
		TRIOLS_REVELATIONS4,
		TRIOLS_REVELATIONS5,
		TRIOLS_REVELATIONS6,
		// TRIOLS_REVELATIONS7,
		// TRIOLS_REVELATIONS8,
		// TRIOLS_REVELATIONS9,
		// TRIOLS_REVELATIONS10,
		// TRIOLS_REVELATIONS11,
	};
	
	private AndreasVanHalter()
	{
		addStartNpc(NPCS);
		addTalkId(NPCS);
		addAttackId(ANDREAS_VAN_HALTER);
		addKillId(ANDREAS_VAN_HALTER);
		SpawnNpcs();
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		final DoorData doors = DoorData.getInstance();
		
		switch (npc.getId())
		{
			case TRIOLS_REVELATIONS1:
			{
				if (!hasQuestItems(player, CHAPEL_KEY))
				{
					return NO_KEY_HTML;
				}
				
				takeItems(player, CHAPEL_KEY, 1);
				doors.getDoor(ALTAR_SECRET_DOOR_1).openMe();
				startQuestTimer(CLOSE_SECRET_DOOR_1_EVENT, 10000, null, null);
				return DOOR_OPEN_HTML;
			}
			case TRIOLS_REVELATIONS2:
			{
				if (!hasQuestItems(player, CHAPEL_KEY))
				{
					return NO_KEY_HTML;
				}
				
				takeItems(player, CHAPEL_KEY, 1);
				doors.getDoor(ALTAR_SECRET_DOOR_2).openMe();
				startQuestTimer(CLOSE_SECRET_DOOR_2_EVENT, 10000, null, null);
				return DOOR_OPEN_HTML;
			}
			case TRIOLS_REVELATIONS5:
			{
				if (!hasQuestItems(player, KEY_OF_DARKNESS))
				{
					return NO_KEY_HTML;
				}
				
				takeItems(player, KEY_OF_DARKNESS, 1);
				doors.getDoor(ALTAR_FLAME_GATE_1).openMe();
				doors.getDoor(ALTAR_INNER_GATE_2).closeMe();
				doors.getDoor(ALTAR_INNER_GATE_1).closeMe();
				return DOOR_OPEN_HTML;
			}
			case TRIOLS_REVELATIONS6:
			{
				if (!hasQuestItems(player, KEY_OF_DARKNESS))
				{
					return NO_KEY_HTML;
				}
				
				takeItems(player, KEY_OF_DARKNESS, 1);
				doors.getDoor(ALTAR_FLAME_GATE_2).openMe();
				doors.getDoor(ALTAR_INNER_GATE_2).closeMe();
				doors.getDoor(ALTAR_INNER_GATE_1).closeMe();
				return DOOR_OPEN_HTML;
			}
		}
		
		return super.onTalk(npc, player);
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		final DoorData doors = DoorData.getInstance();
		
		switch (event)
		{
			case CLOSE_SECRET_DOOR_1_EVENT:
			{
				doors.getDoor(ALTAR_SECRET_DOOR_1).closeMe();
				break;
			}
			case CLOSE_SECRET_DOOR_2_EVENT:
			{
				doors.getDoor(ALTAR_SECRET_DOOR_2).closeMe();
				break;
			}
		}
		
		return event;
	}
	
	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		if (npc.getId() == ANDREAS_VAN_HALTER)
		{
			final DoorData doors = DoorData.getInstance();
			
			doors.getDoor(ALTAR_FLAME_GATE_1).closeMe();
			doors.getDoor(ALTAR_FLAME_GATE_2).closeMe();
			
			doors.getDoor(ALTAR_INNER_GATE_2).openMe();
			doors.getDoor(ALTAR_INNER_GATE_1).openMe();
		}
	}
	
	private void SpawnNpcs()
	{
		addSpawn(TRIOLS_REVELATIONS1, new Location(-18161, -52778, -11013)); // Primeira porta a esquerda
		addSpawn(TRIOLS_REVELATIONS2, new Location(-14590, -52790, -11013)); // Primeira porta a direita
		
		addSpawn(TRIOLS_REVELATIONS3, new Location(-18079, -54816, -10603)); // Primeira porta a esquerda da varanda
		addSpawn(TRIOLS_REVELATIONS4, new Location(-14686, -54745, -10603)); // Primeira porta a direita da varanda
		
		addSpawn(TRIOLS_REVELATIONS5, new Location(-17547, -54920, -10474)); // Primeira porta a esquerda do boss
		addSpawn(TRIOLS_REVELATIONS6, new Location(-15256, -54924, -10474)); // Primeira porta a direita ddo boss
	}
	
	public static void main(String[] args)
	{
		new AndreasVanHalter();
	}
}
