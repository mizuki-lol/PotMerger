package dev.potionmerger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.*;

/**
 * Core logic for merging potions.
 *
 * Rules:
 * - Only POTION, SPLASH_POTION, LINGERING_POTION are accepted
 * - 2 or 3 potions may be placed (rest of the grid must be empty)
 * - All potions must be the same Material type (e.g. all SPLASH)
 * - Effects are merged: if the same effect type appears in multiple potions,
 *   the highest amplifier wins and durations are summed
 * - Result is a single potion of the same Material type carrying all combined effects
 */
public final class PotionMerger {

    private static final Set<Material> POTION_MATERIALS = Set.of(
            Material.POTION,
            Material.SPLASH_POTION,
            Material.LINGERING_POTION
    );

    private PotionMerger() {}

    /**
     * Checks whether the given 3×3 grid (9 slots, row-major) represents a valid
     * merge recipe and returns the resulting ItemStack, or null if not applicable.
     */
    public static ItemStack tryMerge(ItemStack[] matrix) {
        List<ItemStack> potions = collectPotions(matrix);

        // Need exactly 2 or 3 potions, rest must be empty
        if (potions.size() < 2 || potions.size() > 3) return null;
        if (!restIsEmpty(matrix, potions.size())) return null;

        // All must share the same Material
        Material material = potions.get(0).getType();
        if (potions.stream().anyMatch(p -> p.getType() != material)) return null;

        // Merge effects
        Map<PotionEffectType, PotionEffect> merged = new LinkedHashMap<>();

        for (ItemStack potion : potions) {
            PotionMeta meta = (PotionMeta) potion.getItemMeta();
            if (meta == null) continue;

            // Collect effects: custom effects + effects from the base potion type
            List<PotionEffect> effects = new ArrayList<>(meta.getCustomEffects());

            PotionType baseType = meta.getBasePotionType();
            if (baseType != null) {
                effects.addAll(baseType.getPotionEffects());
            }

            for (PotionEffect effect : effects) {
                PotionEffectType type = effect.getType();
                if (merged.containsKey(type)) {
                    PotionEffect existing = merged.get(type);
                    // Keep highest amplifier; sum durations
                    int newAmp = Math.max(existing.getAmplifier(), effect.getAmplifier());
                    int newDur = existing.getDuration() + effect.getDuration();
                    merged.put(type, new PotionEffect(type, newDur, newAmp,
                            existing.isAmbient(), existing.hasParticles(), existing.hasIcon()));
                } else {
                    merged.put(type, effect);
                }
            }
        }

        if (merged.isEmpty()) return null;

        // Build result item
        ItemStack result = new ItemStack(material);
        PotionMeta resultMeta = (PotionMeta) result.getItemMeta();
        if (resultMeta == null) return null;

        // Set base type to AWKWARD so the item renders as a valid potion bottle
        resultMeta.setBasePotionType(PotionType.AWKWARD);

        for (PotionEffect effect : merged.values()) {
            resultMeta.addCustomEffect(effect, true);
        }

        resultMeta.setDisplayName("§5§lСмешанное зелье");
        resultMeta.setLore(buildLore(merged.values()));

        result.setItemMeta(resultMeta);
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<ItemStack> collectPotions(ItemStack[] matrix) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : matrix) {
            if (item != null && POTION_MATERIALS.contains(item.getType())) {
                list.add(item);
            }
        }
        return list;
    }

    private static boolean restIsEmpty(ItemStack[] matrix, int expectedPotions) {
        int found = 0;
        for (ItemStack item : matrix) {
            if (item != null && !item.getType().isAir()) {
                if (POTION_MATERIALS.contains(item.getType())) {
                    found++;
                } else {
                    return false; // Non-potion item present
                }
            }
        }
        return found == expectedPotions;
    }

    private static List<String> buildLore(Collection<PotionEffect> effects) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Эффекты:");
        for (PotionEffect effect : effects) {
            String name = formatEffectName(effect.getType().getName());
            String level = toRoman(effect.getAmplifier() + 1);
            String duration = formatDuration(effect.getDuration());
            lore.add("§a• " + name + " " + level + " §7(" + duration + ")");
        }
        return lore;
    }

    private static String formatEffectName(String rawName) {
        // Convert SPEED -> Speed, NIGHT_VISION -> Night Vision
        String[] parts = rawName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private static String formatDuration(int ticks) {
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        int sec = seconds % 60;
        if (minutes > 0) {
            return String.format("%d:%02d", minutes, sec);
        }
        return sec + "с";
    }
}
