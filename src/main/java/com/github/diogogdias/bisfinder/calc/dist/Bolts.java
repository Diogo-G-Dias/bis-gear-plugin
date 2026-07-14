package com.github.diogogdias.bisfinder.calc.dist;

import com.github.diogogdias.bisfinder.calc.Constants;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.MonsterAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * Port of osrs-dps-calc's src/lib/dists/bolts.ts. These effects fire on normal attacks.
 */
public final class Bolts
{
	private Bolts()
	{
	}

	@Value
	public static class BoltContext
	{
		int rangedLvl;
		int maxHit;
		boolean zcb;
		boolean spec;
		boolean kandarinDiary;
		Monster monster;
	}

	private static double kandarinFactor(BoltContext ctx)
	{
		return ctx.isKandarinDiary() ? 1.1 : 1.0;
	}

	private static HitTransformer bonusDamageTransform(BoltContext ctx, double chance, int bonusDmg, boolean accurateOnly)
	{
		return (h) ->
		{
			if (h.isAccurate() && ctx.isZcb() && ctx.isSpec())
			{
				return HitDistribution.single(1.0, Collections.singletonList(new Hitsplat(h.getDamage() + bonusDmg)));
			}
			if (!h.isAccurate() && accurateOnly)
			{
				return new HitDistribution(Collections.singletonList(
					new WeightedHit(1.0, Collections.singletonList(h))));
			}

			List<WeightedHit> hits = new ArrayList<>(2);
			hits.add(new WeightedHit(chance, Collections.singletonList(
				new Hitsplat(h.getDamage() + bonusDmg, h.isAccurate()))));
			hits.add(new WeightedHit(1 - chance, Collections.singletonList(
				new Hitsplat(h.getDamage(), h.isAccurate()))));
			return new HitDistribution(hits);
		};
	}

	public static HitTransformer opalBolts(BoltContext ctx)
	{
		double chance = 0.05 * kandarinFactor(ctx);
		int bonusDmg = CalcMath.trunc((double) ctx.getRangedLvl() / (ctx.isZcb() ? 9 : 10));

		return bonusDamageTransform(ctx, chance, bonusDmg, false);
	}

	public static HitTransformer pearlBolts(BoltContext ctx)
	{
		double chance = 0.06 * kandarinFactor(ctx);
		int divisor = ctx.getMonster().getAttributes().contains(MonsterAttribute.FIERY) ? 15 : 20;
		int bonusDmg = CalcMath.trunc((double) ctx.getRangedLvl() / (ctx.isZcb() ? divisor - 2 : divisor));

		return bonusDamageTransform(ctx, chance, bonusDmg, false);
	}

	public static HitTransformer diamondBolts(BoltContext ctx)
	{
		double chance = 0.1 * kandarinFactor(ctx);
		int effectMax = CalcMath.trunc((double) ctx.getMaxHit() * (ctx.isZcb() ? 126 : 115) / 100);

		HitDistribution effectDist = HitDistribution.linear(1.0, 0, effectMax);
		return (h) ->
		{
			if (h.isAccurate() && ctx.isZcb() && ctx.isSpec())
			{
				return effectDist;
			}

			List<WeightedHit> hits = new ArrayList<>(effectDist.getHits().size() + 1);
			hits.addAll(effectDist.scaleProbability(chance).getHits());
			hits.add(new WeightedHit(1 - chance, Collections.singletonList(
				new Hitsplat(h.getDamage(), h.isAccurate()))));
			return new HitDistribution(hits);
		};
	}

	public static HitTransformer dragonstoneBolts(BoltContext ctx)
	{
		List<MonsterAttribute> attrs = ctx.getMonster().getAttributes();
		if (attrs.contains(MonsterAttribute.FIERY) || attrs.contains(MonsterAttribute.DRAGON))
		{
			// immune to dragonfire
			return (h) -> new HitDistribution(Collections.singletonList(
				new WeightedHit(1.0, Collections.singletonList(h))));
		}

		double chance = 0.06 * kandarinFactor(ctx);
		int bonusDmg = CalcMath.trunc((double) ctx.getRangedLvl() * 2 / (ctx.isZcb() ? 9 : 10));

		return bonusDamageTransform(ctx, chance, bonusDmg, true);
	}

	public static HitTransformer onyxBolts(BoltContext ctx)
	{
		if (ctx.getMonster().getAttributes().contains(MonsterAttribute.UNDEAD))
		{
			// immune to life leech
			return (h) -> new HitDistribution(Collections.singletonList(
				new WeightedHit(1.0, Collections.singletonList(h))));
		}

		double chance = 0.11 * kandarinFactor(ctx);
		int effectMax = CalcMath.trunc((double) ctx.getMaxHit() * (ctx.isZcb() ? 132 : 120) / 100);

		HitDistribution effectDist = HitDistribution.linear(1.0, 0, effectMax);
		return (h) ->
		{
			if (!h.isAccurate())
			{
				return new HitDistribution(Collections.singletonList(
					new WeightedHit(1.0, Collections.singletonList(h))));
			}
			if (ctx.isZcb() && ctx.isSpec())
			{
				return effectDist;
			}

			List<WeightedHit> hits = new ArrayList<>(effectDist.getHits().size() + 1);
			hits.addAll(effectDist.scaleProbability(chance).getHits());
			hits.add(new WeightedHit(1 - chance, Collections.singletonList(
				new Hitsplat(h.getDamage(), h.isAccurate()))));
			return new HitDistribution(hits);
		};
	}

	public static HitTransformer rubyBolts(BoltContext ctx)
	{
		double chance = 0.06 * kandarinFactor(ctx);

		int cap;
		if (Constants.INFINITE_HEALTH_MONSTERS.contains(ctx.getMonster().getId()))
		{
			cap = ctx.isZcb() ? 66 : 60;
		}
		else
		{
			cap = ctx.isZcb() ? 110 : 100;
		}

		int effectDmg = CalcMath.trunc(
			(double) ctx.getMonster().getInputs().getMonsterCurrentHp() * (ctx.isZcb() ? 22 : 20) / 100);
		HitDistribution effectHit = HitDistribution.single(1.0,
			Collections.singletonList(new Hitsplat(Math.min(cap, effectDmg))));

		return (h) ->
		{
			if (h.isAccurate() && ctx.isZcb() && ctx.isSpec())
			{
				return effectHit;
			}

			List<WeightedHit> hits = new ArrayList<>(effectHit.getHits().size() + 1);
			hits.addAll(effectHit.scaleProbability(chance).getHits());
			hits.add(new WeightedHit(1 - chance, Collections.singletonList(
				new Hitsplat(h.getDamage(), h.isAccurate()))));
			return new HitDistribution(hits);
		};
	}
}
