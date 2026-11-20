package com.molview.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Complete periodic table data plus layout and display colours. */
public final class PeriodicTable {
    private static final List<Element> ELEMENTS = new ArrayList<>();
    private static final Map<String, Element> BY_SYMBOL = new LinkedHashMap<>();
    private static final Map<Integer, Element> BY_NUMBER = new LinkedHashMap<>();

    static {
        String data = """
                1,H,Hydrogen,1,1,nonmetal
                2,He,Helium,18,1,noble-gas
                3,Li,Lithium,1,2,alkali-metal
                4,Be,Beryllium,2,2,alkaline-earth
                5,B,Boron,13,2,metalloid
                6,C,Carbon,14,2,nonmetal
                7,N,Nitrogen,15,2,nonmetal
                8,O,Oxygen,16,2,nonmetal
                9,F,Fluorine,17,2,halogen
                10,Ne,Neon,18,2,noble-gas
                11,Na,Sodium,1,3,alkali-metal
                12,Mg,Magnesium,2,3,alkaline-earth
                13,Al,Aluminium,13,3,post-transition
                14,Si,Silicon,14,3,metalloid
                15,P,Phosphorus,15,3,nonmetal
                16,S,Sulfur,16,3,nonmetal
                17,Cl,Chlorine,17,3,halogen
                18,Ar,Argon,18,3,noble-gas
                19,K,Potassium,1,4,alkali-metal
                20,Ca,Calcium,2,4,alkaline-earth
                21,Sc,Scandium,3,4,transition
                22,Ti,Titanium,4,4,transition
                23,V,Vanadium,5,4,transition
                24,Cr,Chromium,6,4,transition
                25,Mn,Manganese,7,4,transition
                26,Fe,Iron,8,4,transition
                27,Co,Cobalt,9,4,transition
                28,Ni,Nickel,10,4,transition
                29,Cu,Copper,11,4,transition
                30,Zn,Zinc,12,4,transition
                31,Ga,Gallium,13,4,post-transition
                32,Ge,Germanium,14,4,metalloid
                33,As,Arsenic,15,4,metalloid
                34,Se,Selenium,16,4,nonmetal
                35,Br,Bromine,17,4,halogen
                36,Kr,Krypton,18,4,noble-gas
                37,Rb,Rubidium,1,5,alkali-metal
                38,Sr,Strontium,2,5,alkaline-earth
                39,Y,Yttrium,3,5,transition
                40,Zr,Zirconium,4,5,transition
                41,Nb,Niobium,5,5,transition
                42,Mo,Molybdenum,6,5,transition
                43,Tc,Technetium,7,5,transition
                44,Ru,Ruthenium,8,5,transition
                45,Rh,Rhodium,9,5,transition
                46,Pd,Palladium,10,5,transition
                47,Ag,Silver,11,5,transition
                48,Cd,Cadmium,12,5,transition
                49,In,Indium,13,5,post-transition
                50,Sn,Tin,14,5,post-transition
                51,Sb,Antimony,15,5,metalloid
                52,Te,Tellurium,16,5,metalloid
                53,I,Iodine,17,5,halogen
                54,Xe,Xenon,18,5,noble-gas
                55,Cs,Caesium,1,6,alkali-metal
                56,Ba,Barium,2,6,alkaline-earth
                57,La,Lanthanum,3,6,lanthanide
                58,Ce,Cerium,3,6,lanthanide
                59,Pr,Praseodymium,3,6,lanthanide
                60,Nd,Neodymium,3,6,lanthanide
                61,Pm,Promethium,3,6,lanthanide
                62,Sm,Samarium,3,6,lanthanide
                63,Eu,Europium,3,6,lanthanide
                64,Gd,Gadolinium,3,6,lanthanide
                65,Tb,Terbium,3,6,lanthanide
                66,Dy,Dysprosium,3,6,lanthanide
                67,Ho,Holmium,3,6,lanthanide
                68,Er,Erbium,3,6,lanthanide
                69,Tm,Thulium,3,6,lanthanide
                70,Yb,Ytterbium,3,6,lanthanide
                71,Lu,Lutetium,3,6,lanthanide
                72,Hf,Hafnium,4,6,transition
                73,Ta,Tantalum,5,6,transition
                74,W,Tungsten,6,6,transition
                75,Re,Rhenium,7,6,transition
                76,Os,Osmium,8,6,transition
                77,Ir,Iridium,9,6,transition
                78,Pt,Platinum,10,6,transition
                79,Au,Gold,11,6,transition
                80,Hg,Mercury,12,6,transition
                81,Tl,Thallium,13,6,post-transition
                82,Pb,Lead,14,6,post-transition
                83,Bi,Bismuth,15,6,post-transition
                84,Po,Polonium,16,6,post-transition
                85,At,Astatine,17,6,halogen
                86,Rn,Radon,18,6,noble-gas
                87,Fr,Francium,1,7,alkali-metal
                88,Ra,Radium,2,7,alkaline-earth
                89,Ac,Actinium,3,7,actinide
                90,Th,Thorium,3,7,actinide
                91,Pa,Protactinium,3,7,actinide
                92,U,Uranium,3,7,actinide
                93,Np,Neptunium,3,7,actinide
                94,Pu,Plutonium,3,7,actinide
                95,Am,Americium,3,7,actinide
                96,Cm,Curium,3,7,actinide
                97,Bk,Berkelium,3,7,actinide
                98,Cf,Californium,3,7,actinide
                99,Es,Einsteinium,3,7,actinide
                100,Fm,Fermium,3,7,actinide
                101,Md,Mendelevium,3,7,actinide
                102,No,Nobelium,3,7,actinide
                103,Lr,Lawrencium,3,7,actinide
                104,Rf,Rutherfordium,4,7,transition
                105,Db,Dubnium,5,7,transition
                106,Sg,Seaborgium,6,7,transition
                107,Bh,Bohrium,7,7,transition
                108,Hs,Hassium,8,7,transition
                109,Mt,Meitnerium,9,7,transition
                110,Ds,Darmstadtium,10,7,transition
                111,Rg,Roentgenium,11,7,transition
                112,Cn,Copernicium,12,7,transition
                113,Nh,Nihonium,13,7,post-transition
                114,Fl,Flerovium,14,7,post-transition
                115,Mc,Moscovium,15,7,post-transition
                116,Lv,Livermorium,16,7,post-transition
                117,Ts,Tennessine,17,7,halogen
                118,Og,Oganesson,18,7,noble-gas
                """;

        for (String line : data.strip().split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] p = line.split(",");
            Element e = new Element(Integer.parseInt(p[0].trim()), p[1].trim(), p[2].trim(),
                    Integer.parseInt(p[3].trim()), Integer.parseInt(p[4].trim()), p[5].trim());
            ELEMENTS.add(e);
            BY_SYMBOL.put(e.symbol(), e);
            BY_NUMBER.put(e.atomicNumber(), e);
        }
    }

    private PeriodicTable() {
    }

    public static List<Element> all() {
        return Collections.unmodifiableList(ELEMENTS);
    }

    public static Element bySymbol(String symbol) {
        return BY_SYMBOL.get(symbol);
    }

    public static Element byNumber(int number) {
        return BY_NUMBER.get(number);
    }

    public static String nameOf(String symbol) {
        Element e = bySymbol(symbol);
        return e == null ? symbol : e.name();
    }

    /** 1..18 for the main table, 0 for the f-block. */
    public static int gridColumn(Element e) {
        if (e.category().equals("lanthanide")) {
            return 3 + (e.atomicNumber() - 57);
        }
        if (e.category().equals("actinide")) {
            return 3 + (e.atomicNumber() - 89);
        }
        return e.group();
    }

    /** Main-table period 1..7, or 9/10 for the separated f-block rows. */
    public static int gridRow(Element e) {
        if (e.category().equals("lanthanide")) {
            return 9;
        }
        if (e.category().equals("actinide")) {
            return 10;
        }
        return e.period();
    }

    public static String categoryColor(String category) {
        return switch (category) {
            case "alkali-metal" -> "#f7b7ae";
            case "alkaline-earth" -> "#f9d39d";
            case "transition" -> "#ffe29a";
            case "post-transition" -> "#c5e8b4";
            case "metalloid" -> "#a9dfd3";
            case "nonmetal" -> "#a9d8f5";
            case "halogen" -> "#9fd8c7";
            case "noble-gas" -> "#cabdf5";
            case "lanthanide" -> "#f7bcd6";
            case "actinide" -> "#e7b7df";
            default -> "#dfe6ef";
        };
    }

    public static String categoryName(String category) {
        return switch (category) {
            case "alkali-metal" -> "Alkali metal";
            case "alkaline-earth" -> "Alkaline earth metal";
            case "transition" -> "Transition metal";
            case "post-transition" -> "Post-transition metal";
            case "metalloid" -> "Metalloid";
            case "nonmetal" -> "Nonmetal";
            case "halogen" -> "Halogen";
            case "noble-gas" -> "Noble gas";
            case "lanthanide" -> "Lanthanide";
            case "actinide" -> "Actinide";
            default -> category;
        };
    }

    /** Dark, readable element colour used for atom text. */
    public static String atomColor(String symbol) {
        return switch (symbol) {
            case "H" -> "#475569";
            case "C" -> "#1f2937";
            case "N" -> "#1d4ed8";
            case "O" -> "#dc2626";
            case "F", "Cl", "Br", "I", "At", "Ts" -> "#15803d";
            case "S" -> "#b45309";
            case "P" -> "#c2410c";
            case "B" -> "#be185d";
            case "Si" -> "#6d28d9";
            case "Se", "Te" -> "#0f766e";
            case "Li", "Na", "K", "Rb", "Cs", "Fr" -> "#8a5a00";
            case "Mg", "Ca", "Sr", "Ba", "Ra" -> "#7c6500";
            case "Fe", "Co", "Ni", "Cu", "Zn", "Mn" -> "#475569";
            case "R" -> "#7c3aed";
            case "X" -> "#0e7490";
            default -> {
                Element e = bySymbol(symbol);
                if (e != null && e.category().equals("noble-gas")) {
                    yield "#6d28d9";
                }
                yield "#334155";
            }
        };
    }
}
