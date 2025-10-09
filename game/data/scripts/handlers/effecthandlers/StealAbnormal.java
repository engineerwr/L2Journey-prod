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
package handlers.effecthandlers;

import java.util.List;

import com.l2journey.commons.util.Rnd;
import com.l2journey.gameserver.model.StatSet;
import com.l2journey.gameserver.model.actor.Creature;
import com.l2journey.gameserver.model.conditions.Condition;
import com.l2journey.gameserver.model.effects.AbstractEffect;
import com.l2journey.gameserver.model.effects.EffectType;
import com.l2journey.gameserver.model.skill.BuffInfo;
import com.l2journey.gameserver.model.skill.EffectScope;
import com.l2journey.gameserver.model.skill.Skill;
import com.l2journey.gameserver.model.skill.enums.SkillFinishType;
import com.l2journey.gameserver.model.skill.skillVariation.CancelRestrictions;
import com.l2journey.gameserver.model.stats.Formulas;

/**
 * Steal Abnormal effect implementation.
 * @author Adry_85, Zoey76, KingHanker, Zoinha
 */
public class StealAbnormal extends AbstractEffect
{
	private final String _slot;
	private final int _chance;
	private final int _rate;
	private final int _max;
	
	public StealAbnormal(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
		
		_slot = params.getString("slot", null);
		_chance = params.getInt("chance", 0);
		_rate = params.getInt("rate", 0);
		_max = params.getInt("max", 0);
	}
	
	@Override
	public EffectType getEffectType()
	{
		return EffectType.STEAL_ABNORMAL;
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		if ((effector == effected) || !effected.isPlayer())
		{
			return;
		}
		
		if ((Rnd.get(100) >= _chance) || !CancelRestrictions.canTakeCancel(effected))
		{
			sendFailureMessage(effector, effected);
			return;
		}
		
		final List<BuffInfo> buffstoSteal = Formulas.calcCancelStealEffects(effector, effected, skill, _slot, _rate, _max);
		if (buffstoSteal.isEmpty())
		{
			return;
		}
		
		for (BuffInfo infoToSteal : buffstoSteal)
		{
			// Cria um novo BuffInfo invertendo effected e effector.
			final BuffInfo stolen = new BuffInfo(effected, effector, infoToSteal.getSkill());
			stolen.setAbnormalTime(infoToSteal.getTime()); // Copia o tempo restante.
			
			// Aplica o efeito roubado ao novo alvo.
			infoToSteal.getSkill().applyEffectScope(EffectScope.GENERAL, stolen, true, true);
			
			// Remove buff do antigo dono e aplicar ao novo.
			effected.getEffectList().remove(SkillFinishType.REMOVED, infoToSteal);
			effector.getEffectList().add(stolen);
		}
		
		sendSuccessMessage(effector, effected);
	}
	
	private void sendFailureMessage(Creature effector, Creature effected)
	{
		String effectedName = effected.getName();
		String effectorName = effector.getName();
		
		if (effectedName != null)
		{
			effector.sendMessage(effectedName + " resisted the buff steal.");
		}
		if (effectorName != null)
		{
			effected.sendMessage("You resisted " + effectorName + "'s buff steal.");
		}
	}
	
	private void sendSuccessMessage(Creature effector, Creature effected)
	{
		String effectedName = effected.getName();
		String effectorName = effector.getName();
		
		if (effectedName != null)
		{
			effector.sendMessage("You have stolen a buff from " + effectedName + ".");
		}
		if (effectorName != null)
		{
			effected.sendMessage(effectorName + " has stolen one of your buffs.");
		}
	}
}