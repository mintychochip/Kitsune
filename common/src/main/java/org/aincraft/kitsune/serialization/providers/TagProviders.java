package org.aincraft.kitsune.serialization.providers;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.aincraft.kitsune.Item;
import org.aincraft.kitsune.api.serialization.TagProvider;
import org.aincraft.kitsune.api.serialization.TagProviderRegistry;
import org.aincraft.kitsune.api.serialization.Tags;

/**
 * Registers generic/vanilla tag providers that work across all platforms.
 */
public final class TagProviders {
    private TagProviders() {}

    /** Material tag mappings for common materials */
    private static final Map<Predicate<String>, Set<String>> MATERIAL_TAGS = Map.of(
        TagMatch.contains("NETHERITE"), Set.of("netherite"),
        TagMatch.contains("DIAMOND"), Set.of("diamond"),
        TagMatch.contains("IRON"), Set.of("iron"),
        TagMatch.any(TagMatch.contains("GOLD"), TagMatch.contains("GOLDEN")), Set.of("gold", "golden"),
        TagMatch.contains("STONE"), Set.of("stone"),
        TagMatch.any(TagMatch.contains("WOOD"), TagMatch.contains("WOODEN")), Set.of("wood", "wooden"),
        TagMatch.contains("LEATHER"), Set.of("leather"),
        TagMatch.contains("CHAINMAIL"), Set.of("chainmail")
    );

    /** Helper method to add material tags */
    private static void addMaterialTags(String material, Tags tags) {
        for (Map.Entry<Predicate<String>, Set<String>> entry : MATERIAL_TAGS.entrySet()) {
            if (entry.getKey().test(material)) {
                tags.addAll(entry.getValue());
                break;
            }
        }
    }

    /** 1. Enchantment provider - adds enchantment tags */
    public static final TagProvider ENCHANTMENT = (tags, item) -> {
        var enchantments = item.enchantments();
        if (!enchantments.isEmpty()) {
            tags.add("enchanted");
        }
        for (var entry : enchantments.entrySet()) {
            tags.add(entry.getKey())
                .add(entry.getKey() + "_" + entry.getValue());
        }
    };

    /** 2. Solid block provider - adds block type tags */
    public static final TagProvider SOLID_BLOCK = (tags, item) -> {
        tags.addIf(item.isSolid(), "solid")
            .addIf(item.isBlock(), "block");
    };

    /** 3. Block opacity provider - adds transparency tags */
    public static final TagProvider BLOCK_OPACITY = (tags, item) -> {
        tags.addIf(item.isOccluding(), "occluding")
            .addIf(item.isBlock() && !item.isOccluding(), "transparent");
    };

    /** 4. Block gravity provider - adds gravity tags */
    public static final TagProvider BLOCK_GRAVITY = (tags, item) -> {
        tags.addIf(item.hasGravity(), "gravity", "falling");
    };

    /** 5. Redstone provider - adds redstone-related tags */
    public static final TagProvider REDSTONE = (tags, item) -> {
        String m = item.material().toUpperCase();
        tags.addIf(m.contains("REDSTONE"), "redstone")
            .addIf(m.contains("POWERED"), "powered")
            .addIf(m.contains("COMPARATOR"), "comparator")
            .addIf(m.contains("REPEATER"), "repeater");
    };

    /** 6. Block color provider - adds color tags */
    public static final TagProvider BLOCK_COLOR = (tags, item) -> {
        String[] colors = {"white","orange","magenta","light_blue","yellow","lime","pink","gray",
            "light_gray","cyan","purple","blue","brown","green","red","black"};
        String m = item.material().toUpperCase();
        for (String color : colors) {
            if (m.startsWith(color.toUpperCase() + "_")) {
                tags.add(color);
                break;
            }
        }
    };

    /** 7. Block material provider - adds material type tags */
    public static final TagProvider BLOCK_MATERIAL = (tags, item) -> {
        String u = item.material().toUpperCase();

        // Glass
        tags.when(u.contains("GLASS"), t -> t.add("glass").addIf(u.contains("PANE"), "pane"));

        // Wool
        tags.when(u.contains("WOOL"), t -> t.add("wool", "soft"));

        // Concrete
        tags.when(u.contains("CONCRETE"), t -> t.add("concrete").addIf(u.contains("POWDER"), "powder"));

        // Terracotta
        tags.when(u.contains("TERRACOTTA"), t -> t.add("terracotta", "clay").addIf(u.contains("GLAZED"), "glazed"));

        // Candles
        tags.when(u.contains("CANDLE"), t -> t.add("candle", "lightsource"));

        // Carpet
        tags.when(u.contains("CARPET"), t -> t.add("carpet", "flooring"));

        // Beds
        tags.when(u.contains("BED") && !u.contains("BEDROCK"), t -> t.add("bed", "furniture"));

        // Banners
        tags.when(u.contains("BANNER"), t -> t.add("banner", "decorative"));

        // Shulker boxes
        tags.when(u.contains("SHULKER"), t -> t.add("shulker", "storage", "container"));

        // Wood types
        tags.when(u.contains("OAK") && !u.contains("DARK_OAK"), t -> t.add("oak", "wood"));
        tags.when(u.contains("SPRUCE"), t -> t.add("spruce", "wood"));
        tags.when(u.contains("BIRCH"), t -> t.add("birch", "wood"));
        tags.when(u.contains("JUNGLE"), t -> t.add("jungle", "wood"));
        tags.when(u.contains("ACACIA"), t -> t.add("acacia", "wood"));
        tags.when(u.contains("DARK_OAK"), t -> t.add("darkoak", "wood"));
        tags.when(u.contains("MANGROVE"), t -> t.add("mangrove", "wood"));
        tags.when(u.contains("CHERRY"), t -> t.add("cherry", "wood"));
        tags.when(u.contains("BAMBOO"), t -> t.add("bamboo", "wood"));
        tags.when(u.contains("CRIMSON"), t -> t.add("crimson", "netherstem"));
        tags.when(u.contains("WARPED"), t -> t.add("warped", "netherstem"));

        // Stone variants
        tags.when(u.contains("STONE") && !u.contains("REDSTONE") && !u.contains("GLOWSTONE") && !u.contains("SANDSTONE"),
            t -> t.add("stone"));
        tags.when(u.contains("COBBLESTONE"), t -> t.add("cobblestone", "cobble"));
        tags.when(u.contains("DEEPSLATE"), t -> t.add("deepslate"));
        tags.when(u.contains("BRICK"), t -> t.add("brick"));
        tags.when(u.contains("SANDSTONE"), t -> t.add("sandstone"));

        // Ores
        tags.when(u.endsWith("_ORE"), t -> t.add("ore", "mineable"));
    };

    /** 8. Mineral provider - adds mineral/ore tags */
    public static final TagProvider MINERAL = (tags, item) -> {
        String u = item.material().toUpperCase();

        // Ores
        tags.when(u.endsWith("_ORE") || u.equals("NETHER_GOLD_ORE") || u.equals("ANCIENT_DEBRIS"),
            t -> t.add("ore", "mineral", "mineable"));

        // Raw materials
        tags.when(u.startsWith("RAW_"), t -> t.add("raw", "mineral", "smeltable"));

        // Ingots
        tags.when(u.endsWith("_INGOT") || u.equals("NETHERITE_INGOT") || u.equals("COPPER_INGOT"),
            t -> t.add("ingot", "mineral", "metal", "refined"));

        // Nuggets
        tags.when(u.endsWith("_NUGGET"), t -> t.add("nugget", "mineral", "metal"));

        // Gems
        tags.when(Set.of("DIAMOND","EMERALD","AMETHYST_SHARD","LAPIS_LAZULI","PRISMARINE_SHARD",
                "PRISMARINE_CRYSTALS","QUARTZ","NETHER_QUARTZ").contains(u),
            t -> t.add("gem", "mineral", "precious"));

        // Specific minerals
        tags.when(u.contains("DIAMOND"), t -> t.add("diamond", "mineral"));
        tags.when(u.contains("EMERALD"), t -> t.add("emerald", "mineral"));
        tags.when(u.contains("GOLD") || u.contains("GOLDEN"), t -> t.add("gold", "mineral"));
        tags.when(u.contains("IRON"), t -> t.add("iron", "mineral"));
        tags.when(u.contains("COPPER"), t -> t.add("copper", "mineral"));
        tags.when(u.contains("NETHERITE"), t -> t.add("netherite", "mineral"));
        tags.when(u.contains("LAPIS"), t -> t.add("lapis", "mineral"));
        tags.when(u.contains("REDSTONE"), t -> t.add("redstone", "mineral"));
        tags.when(u.contains("QUARTZ"), t -> t.add("quartz", "mineral"));
        tags.when(u.contains("AMETHYST"), t -> t.add("amethyst", "mineral"));
        tags.when(u.contains("COAL") && !u.contains("CHARCOAL"), t -> t.add("coal", "mineral"));

        // Mineral blocks
        tags.when(u.endsWith("_BLOCK") && (u.contains("DIAMOND")||u.contains("EMERALD")||u.contains("GOLD")||
                u.contains("IRON")||u.contains("COPPER")||u.contains("NETHERITE")||u.contains("LAPIS")||
                u.contains("REDSTONE")||u.contains("COAL")||u.contains("AMETHYST")||u.contains("QUARTZ")||
                u.contains("RAW_")),
            t -> t.add("mineralblock", "storage"));
    };

    /** 9. Weapon provider - adds weapon tags */
    public static final TagProvider WEAPON = (tags, item) -> {
        String u = item.material().toUpperCase();

        // Swords
        tags.when(u.endsWith("_SWORD"), t -> {
            t.add("weapon", "sword", "melee", "combat");
            addMaterialTags(u, t);
        });

        // Axes (weapons)
        tags.when(u.endsWith("_AXE") && !u.contains("PICKAXE"), t -> {
            t.add("weapon", "axe", "melee", "combat");
            addMaterialTags(u, t);
        });

        // Bows
        tags.when(u.equals("BOW"), t -> t.add("weapon", "bow", "ranged", "projectile", "combat"));
        tags.when(u.equals("CROSSBOW"), t -> t.add("weapon", "crossbow", "ranged", "projectile", "combat"));

        // Trident
        tags.when(u.equals("TRIDENT"), t -> t.add("weapon", "trident", "melee", "ranged", "throwable", "combat"));

        // Mace
        tags.when(u.equals("MACE"), t -> t.add("weapon", "mace", "melee", "combat", "heavyhitter"));

        // Arrows
        tags.when(u.contains("ARROW"), t -> {
            t.add("ammo", "ammunition", "projectile", "combat");
            t.addIf(u.equals("SPECTRAL_ARROW"), "spectral");
            t.addIf(u.equals("TIPPED_ARROW"), "tipped", "potion");
        });

        // Firework
        tags.when(u.equals("FIREWORK_ROCKET"), t -> t.add("ammo", "firework", "projectile", "elytra"));

        // Throwables
        tags.when(u.equals("SNOWBALL") || u.equals("EGG"), t -> t.add("weapon", "throwable", "projectile"));
        tags.when(u.equals("ENDER_PEARL"), t -> t.add("throwable", "teleport", "projectile"));
        tags.when(u.equals("WIND_CHARGE"), t -> t.add("weapon", "throwable", "projectile", "knockback"));

        // Shield
        tags.when(u.equals("SHIELD"), t -> t.add("weapon", "shield", "defense", "combat", "blocking"));
    };

    /** 10. Tool provider - adds tool tags */
    public static final TagProvider TOOL = (tags, item) -> {
        String u = item.material().toUpperCase();

        // Pickaxes, Shovels, Hoes
        tags.when(u.endsWith("_PICKAXE"), t -> { t.add("tool", "pickaxe", "mining", "breaking"); addMaterialTags(u, t); });
        tags.when(u.endsWith("_SHOVEL"), t -> { t.add("tool", "shovel", "digging", "breaking"); addMaterialTags(u, t); });
        tags.when(u.endsWith("_HOE"), t -> { t.add("tool", "hoe", "farming", "tilling"); addMaterialTags(u, t); });

        // Axes (tools)
        tags.when(u.endsWith("_AXE") && !u.contains("PICKAXE"), t -> {
            t.add("tool", "axe", "woodcutting", "chopping", "breaking");
            addMaterialTags(u, t);
        });

        // Other tools
        tags.when(u.equals("SHEARS"), t -> t.add("tool", "shears", "shearing", "harvesting"));
        tags.when(u.equals("FLINT_AND_STEEL"), t -> t.add("tool", "flintandsteel", "fire", "igniter"));
        tags.when(u.equals("FISHING_ROD"), t -> t.add("tool", "fishingrod", "fishing", "catching"));
        tags.when(u.equals("CARROT_ON_A_STICK") || u.equals("WARPED_FUNGUS_ON_A_STICK"), t -> t.add("tool", "riding", "control"));
        tags.when(u.equals("LEAD"), t -> t.add("tool", "lead", "leash", "mob"));
        tags.when(u.equals("NAME_TAG"), t -> t.add("tool", "nametag", "naming", "mob"));
        tags.when(u.equals("BRUSH"), t -> t.add("tool", "brush", "archaeology", "excavation"));
        tags.when(u.equals("SPYGLASS"), t -> t.add("tool", "spyglass", "zoom", "scouting"));

        // Compass
        tags.when(u.equals("COMPASS") || u.equals("RECOVERY_COMPASS"), t -> {
            t.add("tool", "compass", "navigation");
            t.addIf(u.equals("RECOVERY_COMPASS"), "recovery", "death");
        });

        tags.when(u.equals("CLOCK"), t -> t.add("tool", "clock", "time"));

        // Maps
        tags.when(u.contains("MAP"), t -> {
            t.add("tool", "map", "navigation");
            t.addIf(u.equals("FILLED_MAP"), "filled");
        });

        // Buckets
        tags.when(u.contains("BUCKET"), t -> {
            t.add("tool", "bucket", "container");
            t.addIf(u.equals("WATER_BUCKET"), "water")
             .addIf(u.equals("LAVA_BUCKET"), "lava")
             .addIf(u.equals("MILK_BUCKET"), "milk")
             .addIf(u.equals("POWDER_SNOW_BUCKET"), "powdersnow");
            t.when(u.contains("FISH_BUCKET") || u.equals("AXOLOTL_BUCKET") || u.equals("TADPOLE_BUCKET"),
                bt -> bt.add("mobbucket", "mob"));
        });

        // Other
        tags.when(u.equals("BONE_MEAL"), t -> t.add("tool", "bonemeal", "farming", "growth"));
        tags.when(u.equals("WRITABLE_BOOK") || u.equals("WRITTEN_BOOK"), t -> t.add("tool", "book", "writing"));
    };

    /** 11. Armor provider - adds armor tags */
    public static final TagProvider ARMOR = (tags, item) -> {
        String u = item.material().toUpperCase();

        // Helmets
        tags.when(TagMatch.any(TagMatch.endsWith("_HELMET"), TagMatch.equals("TURTLE_HELMET")).test(u), t -> {
            t.add("armor", "helmet", "headgear", "head", "wearable");
            addMaterialTags(u, t);
            t.addIf(u.equals("TURTLE_HELMET"), "turtle", "waterbreathing");
        });

        // Chestplates, Leggings, Boots
        tags.when(u.endsWith("_CHESTPLATE"), t -> { t.add("armor", "chestplate", "chest", "body", "wearable"); addMaterialTags(u, t); });
        tags.when(u.endsWith("_LEGGINGS"), t -> { t.add("armor", "leggings", "pants", "legs", "wearable"); addMaterialTags(u, t); });
        tags.when(u.endsWith("_BOOTS"), t -> { t.add("armor", "boots", "footwear", "feet", "wearable"); addMaterialTags(u, t); });

        // Elytra
        tags.when(u.equals("ELYTRA"), t -> t.add("armor", "elytra", "wings", "chest", "wearable", "flying", "gliding"));

        // Horse armor
        tags.when(u.contains("_HORSE_ARMOR"), t -> {
            t.add("armor", "horsearmor", "horse", "mount", "pet");
            t.addIf(u.contains("IRON"), "iron")
             .addIf(u.contains("GOLDEN") || u.contains("GOLD"), "gold")
             .addIf(u.contains("DIAMOND"), "diamond")
             .addIf(u.contains("LEATHER"), "leather");
        });

        // Wolf armor
        tags.when(u.equals("WOLF_ARMOR"), t -> t.add("armor", "wolfarmor", "wolf", "pet"));

        // Leather/Chainmail special
        tags.when(u.startsWith("LEATHER_"), t -> t.add("leather", "dyeable"));
        tags.when(u.startsWith("CHAINMAIL_"), t -> t.add("chainmail", "chain"));

        // Heads/skulls
        tags.when(u.contains("_HEAD") || u.contains("_SKULL") || u.equals("PLAYER_HEAD") || u.equals("DRAGON_HEAD"), t -> {
            t.add("head", "headgear", "wearable", "decorative");
            t.addIf(u.contains("SKELETON"), "skeleton")
             .addIf(u.contains("ZOMBIE"), "zombie")
             .addIf(u.contains("CREEPER"), "creeper")
             .addIf(u.contains("WITHER"), "wither")
             .addIf(u.contains("DRAGON"), "dragon")
             .addIf(u.contains("PIGLIN"), "piglin")
             .addIf(u.contains("PLAYER"), "player");
        });

        // Carved pumpkin
        tags.when(u.equals("CARVED_PUMPKIN"), t -> t.add("head", "headgear", "wearable", "pumpkin", "enderman"));
    };

    /** 12. Storage provider - adds storage container tags */
    public static final TagProvider STORAGE = (tags, item) -> {
        String u = item.material().toUpperCase();

        tags.when(item.hasBundle(), t -> t.add("bundle", "storage", "container"));
        tags.when(item.hasShulkerContents(), t -> t.add("shulkerbox", "storage", "container"));
        tags.when(u.contains("SHULKER_BOX"), t -> t.add("shulkerbox", "storage", "container"));
        tags.when(u.contains("CHEST"), t -> t.add("chest", "storage"));
        tags.when(u.contains("BARREL"), t -> t.add("barrel", "storage"));
        tags.when(u.equals("BUNDLE"), t -> t.add("bundle", "storage", "container"));
    };

    /** 13. Unbreakable provider - adds unbreakable tag */
    public static final TagProvider UNBREAKABLE = (tags, item) -> {
        tags.addIf(item.isUnbreakable(), "unbreakable");
    };

    /**
     * Register all generic tag providers to the registry.
     */
    public static void registerDefaults(TagProviderRegistry registry) {
        registry.register(ENCHANTMENT);
        registry.register(SOLID_BLOCK);
        registry.register(BLOCK_OPACITY);
        registry.register(BLOCK_GRAVITY);
        registry.register(REDSTONE);
        registry.register(BLOCK_COLOR);
        registry.register(BLOCK_MATERIAL);
        registry.register(MINERAL);
        registry.register(WEAPON);
        registry.register(TOOL);
        registry.register(ARMOR);
        registry.register(STORAGE);
        registry.register(UNBREAKABLE);
    }
}
