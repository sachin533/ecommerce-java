package com.shopsmart.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared seed data: the 26-product catalog (mirrors the .NET SeedService).
 * Variants are explicit lists of maps (no cleverness).
 */
public final class SeedCatalog {

    private SeedCatalog() {}

    public record Prod(String key, String title, String category, double price,
                       List<String> tags, Map<String, Object> attrs,
                       List<Map<String, Object>> variants) {}

    private static Map<String, Object> V(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    public static List<Prod> catalog() {
        List<Prod> out = new ArrayList<>();
        out.add(new Prod("WM", "Wireless Mouse", "peripherals", 50.16,
                List.of("wireless", "usb", "office"),
                V("color", "black", "dpi", 1600, "battery", "AA"),
                List.of(V("sku", "WM-001-BLK", "color", "black", "stock", 40),
                        V("sku", "WM-001-WHT", "color", "white", "stock", 12))));
        out.add(new Prod("MK", "Mechanical Keyboard", "peripherals", 100.34,
                List.of("keyboard", "office"),
                V("layout", "TKL", "backlight", true),
                List.of(V("sku", "MK-002-BLU", "switch", "blue", "stock", 25),
                        V("sku", "MK-002-BRN", "switch", "brown", "stock", 30))));
        out.add(new Prod("EARBUDS", "Wireless Earbuds", "audio", 79.99,
                List.of("wireless", "bluetooth", "audio"),
                V("battery_life_h", 24, "anc", false),
                List.of(V("sku", "WE-003-BLK", "color", "black", "stock", 50))));
        out.add(new Prod("HUB", "USB-C Hub 7-in-1", "cables", 45.00,
                List.of("usb-c", "hub"),
                V("ports", List.of("HDMI", "USB-A x3", "SD", "PD 100W")),
                List.of(V("sku", "UH-004", "color", "grey", "stock", 35))));
        out.add(new Prod("LAMP", "Desk Lamp LED", "office", 32.50,
                List.of("lamp", "led"),
                V("lumens", 800, "dimmable", true),
                List.of(V("sku", "DL-005-WHT", "color", "white", "stock", 20),
                        V("sku", "DL-005-BLK", "color", "black", "stock", 22))));
        out.add(new Prod("HEADPHONES", "Noise Cancelling Headphones", "audio", 199.00,
                List.of("audio", "anc", "premium"),
                V("battery_life_h", 40, "bluetooth", "5.3"),
                List.of(V("sku", "NH-006-BLK", "color", "black", "stock", 15))));
        out.add(new Prod("WKEYBOARD", "Wireless Keyboard", "peripherals", 119.99,
                List.of("wireless", "keyboard"),
                V("layout", "full", "battery", "AAA x2"),
                List.of(V("sku", "WK-007-WHT", "color", "white", "stock", 18))));
        out.add(new Prod("WEBCAM", "1080p Webcam", "peripherals", 59.99,
                List.of("video", "usb"),
                V("resolution", "1080p", "mic", "dual"),
                List.of(V("sku", "WC-008", "color", "black", "stock", 28))));
        out.add(new Prod("MOUSEPAD", "Gaming Mouse Pad XL", "peripherals", 14.50,
                List.of("desk", "gaming"),
                V("size", "XL", "thickness_mm", 4),
                List.of(V("sku", "MP-009", "color", "black", "stock", 60))));
        out.add(new Prod("WEBCAMPRO", "4K Webcam Pro", "peripherals", 149.99,
                List.of("video", "premium"),
                V("resolution", "4K", "autofocus", true),
                List.of(V("sku", "WC-010", "color", "black", "stock", 10))));
        out.add(new Prod("ERGO", "Ergonomic Vertical Mouse", "peripherals", 39.99,
                List.of("ergonomic", "usb"),
                V("hand", "right", "dpi", 1200),
                List.of(V("sku", "EM-011", "color", "grey", "stock", 24))));
        out.add(new Prod("SPEAKER", "Wireless Speaker", "audio", 69.99,
                List.of("wireless", "bluetooth", "audio"),
                V("watt", 20, "waterproof", "IPX5"),
                List.of(V("sku", "WS-012-BLU", "color", "blue", "stock", 30))));
        out.add(new Prod("MIC", "USB Microphone", "audio", 54.99,
                List.of("audio", "streaming"),
                V("pattern", "cardioid", "mount", "desk"),
                List.of(V("sku", "UM-013", "color", "black", "stock", 26))));
        out.add(new Prod("EARPHONES", "Wired Earphones", "audio", 19.99,
                List.of("audio", "wired"),
                V("plug", "3.5mm", "mic", true),
                List.of(V("sku", "WE-014-WHT", "color", "white", "stock", 70))));
        out.add(new Prod("SOUNDBAR", "Soundbar 2.1", "audio", 129.99,
                List.of("audio", "tv"),
                V("watt", 120, "subwoofer", "wireless"),
                List.of(V("sku", "SB-015", "color", "black", "stock", 12))));
        out.add(new Prod("HDMI", "HDMI 2.1 Cable 2m", "cables", 12.99,
                List.of("hdmi", "video"),
                V("length_m", 2, "version", "2.1"),
                List.of(V("sku", "HC-016", "color", "black", "stock", 80))));
        out.add(new Prod("USBC", "USB-C to USB-C Cable 2m", "cables", 16.99,
                List.of("usb-c", "charging"),
                V("length_m", 2, "watt", 100),
                List.of(V("sku", "UC-017", "color", "white", "stock", 75))));
        out.add(new Prod("LIGHTNING", "Nylon Braided Lightning Cable", "cables", 18.99,
                List.of("apple", "charging"),
                V("length_m", 1.5, "braided", true),
                List.of(V("sku", "LC-018", "color", "red", "stock", 55))));
        out.add(new Prod("CHARGER", "Wireless Charger Pad", "cables", 29.99,
                List.of("wireless", "charging"),
                V("watt", 15, "coil", "single"),
                List.of(V("sku", "WP-019", "color", "black", "stock", 40))));
        out.add(new Prod("DOCK", "Laptop Docking Station", "cables", 179.99,
                List.of("usb-c", "premium", "office"),
                V("ports", List.of("2xHDMI", "Ethernet", "USB x5")),
                List.of(V("sku", "LD-020", "color", "grey", "stock", 9))));
        out.add(new Prod("DISPLAYPORT", "DisplayPort 1.4 Cable", "cables", 14.99,
                List.of("displayport", "video"),
                V("length_m", 1.8, "version", "1.4"),
                List.of(V("sku", "DP-021", "color", "black", "stock", 45))));
        out.add(new Prod("STAND", "Laptop Stand Aluminum", "office", 89.99,
                List.of("ergonomic", "desk"),
                V("material", "aluminum", "foldable", true),
                List.of(V("sku", "LS-022", "color", "silver", "stock", 33))));
        out.add(new Prod("PRESENTER", "Wireless Presenter", "office", 24.99,
                List.of("wireless", "office"),
                V("range_m", 30, "laser", "red"),
                List.of(V("sku", "WP-023", "color", "black", "stock", 27))));
        out.add(new Prod("MONITORLIGHT", "Monitor Light Bar", "office", 129.00,
                List.of("led", "premium"),
                V("lumens", 500, "usb_powered", true),
                List.of(V("sku", "ML-024", "color", "black", "stock", 14))));
        out.add(new Prod("NOTEBOOK", "A4 Notebook Set", "office", 9.99,
                List.of("paper", "desk"),
                V("pages", 192, "ruled", true),
                List.of(V("sku", "NB-025", "color", "kraft", "stock", 100))));
        out.add(new Prod("ORGANIZER", "Desk Organizer", "office", 21.99,
                List.of("desk", "storage"),
                V("compartments", 6, "material", "bamboo"),
                List.of(V("sku", "DO-026", "color", "natural", "stock", 14))));
        return out;
    }
}

