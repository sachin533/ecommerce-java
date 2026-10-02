package com.shopsmart.service;

import com.shopsmart.model.Order;
import com.shopsmart.model.OrderItem;
import com.shopsmart.model.User;
import com.shopsmart.repository.OrderItemRepository;
import com.shopsmart.repository.OrderRepository;
import com.shopsmart.repository.UserRepository;
import com.shopsmart.repository.OrderSearchRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic demo seed (mirrors the .NET SeedService + the verified
 * per-product image scheme): 8 users, 26 active products (+1 inactive),
 * 40 orders, Wireless Mouse -&gt; Pro rename, ES reindex. Wipes first.
 */
@Service
public class SeedService {

    private final UserRepository users;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final MongoTemplate mongo;
    private final OrderSearchRepository search;
    private final OrderService orderService;

    public SeedService(UserRepository users,
                       OrderRepository orders,
                       OrderItemRepository items,
                       MongoTemplate mongo,
                       OrderSearchRepository search,
                       OrderService orderService) {
        this.users = users;
        this.orders = orders;
        this.items = items;
        this.mongo = mongo;
        this.search = search;
        this.orderService = orderService;
    }

    // ---------------------------------------------------------------- images
    private static final String P = "?auto=format&fit=crop&w=400&q=80";

    private static String U(String id) {
        return "https://images.unsplash.com/photo-" + id + P;
    }

    private static final Map<String, List<String>> POOL = new LinkedHashMap<>();
    private record Rule(String regex, String bucket) {}
    private static final List<Rule> RULES = List.of(
            new Rule("notebook set|paper|organizer|desk mat|mousepad", "stationery"),
            new Rule("macbook|thinkpad|ideapad|inspiron|xps|vivobook|pavilion", "laptop"),
            new Rule("\\blaptop\\b", "laptop"),
            new Rule("galaxy watch|\\bwatch\\b|versa|venu", "watch"),
            new Rule("ipad|galaxy tab|\\btablet\\b", "tablet"),
            new Rule("headphone", "headphones"),
            new Rule("earbud|earphone|airdop|\\bbuds\\b", "earbuds"),
            new Rule("iphone|galaxy s|pixel|oneplus|redmi|xiaomi|\\bsmartphone\\b|\\bphone\\b", "phone"),
            new Rule("keyboard", "keyboard"),
            new Rule("\\bmouse\\b", "mouse"),
            new Rule("monitor|odyssey", "monitor"),
            new Rule("camera|\\beos\\b|d3500|x-t\\d|alpha", "camera"),
            new Rule("speaker|soundbar|flip|stone|emberton|\\bsrs\\b", "speaker"),
            new Rule("television|\\btv\\b|oled|crystal", "tv"),
            new Rule("console|controller|dualsense|xbox|switch pro|gaming|\\bps\\d", "gaming"),
            new Rule("printer|laserjet|pixma|ecotank|\\bdcp\\b", "printer"),
            new Rule("router|archer|\\bmesh\\b|wifi", "router"),
            new Rule("ssd|\\bhdd\\b|hard drive|barracuda|\\b\\d+\\s?gb\\b|\\b\\d+\\s?tb\\b", "ssd"),
            new Rule("lamp|light bar", "lamp"),
            new Rule("\\bhub\\b|dock", "hub"),
            new Rule("power bank|powerbank", "powerbank"),
            new Rule("webcam", "webcam"),
            new Rule("microphone|\\bmic\\b", "mic"),
            new Rule("cable|charger|lightning", "cable"),
            new Rule("\\bstand\\b", "laptop"),
            new Rule("presenter", "lamp"),
            new Rule("wh-\\d", "headphones"));

    private static final Map<String, String> FALLBACK = Map.ofEntries(
            Map.entry("Smartphones", "phone"), Map.entry("Laptops", "laptop"),
            Map.entry("Tablets", "tablet"), Map.entry("Headphones", "headphones"),
            Map.entry("Earbuds", "earbuds"), Map.entry("Smart Watches", "watch"),
            Map.entry("Cameras", "camera"), Map.entry("Monitors", "monitor"),
            Map.entry("Keyboards", "keyboard"), Map.entry("Mice", "mouse"),
            Map.entry("Gaming", "gaming"), Map.entry("Accessories", "powerbank"),
            Map.entry("Televisions", "tv"), Map.entry("Speakers", "speaker"),
            Map.entry("Storage Devices", "ssd"), Map.entry("Networking", "router"),
            Map.entry("Printers", "printer"), Map.entry("peripherals", "keyboard"),
            Map.entry("audio", "headphones"), Map.entry("cables", "cable"),
            Map.entry("office", "lamp"));

    static final java.util.function.Function<String, String> U =
            id -> "https://images.unsplash.com/photo-" + id + "?auto=format&fit=crop&w=400&q=80";

    static {
        POOL.put("laptop", List.of(U.apply("1517336714731-489689fd1ca8"), U.apply("1593642632823-8f785ba67e45"),
                U.apply("1460925895917-afdab827c52f"), U.apply("1496181133206-80ce9b88a853")));
        POOL.put("phone", List.of(U.apply("1610945415295-d9bbf067e59c"), U.apply("1580910051074-3eb694886505"),
                U.apply("1598327105666-5b89351aff97"), U.apply("1511707171634-5f897ff02aa9")));
        POOL.put("headphones", List.of(U.apply("1545127398-14699f92334b"), U.apply("1484704849700-f032a568e944"),
                U.apply("1583394838336-acd977736f90"), U.apply("1505740420928-5e560c06d30e")));
        POOL.put("keyboard", List.of(U.apply("1618384887929-16ec33fab9ef"), U.apply("1547394765-185e1e68f34e"),
                U.apply("1587829741301-dc798b83add3")));
        POOL.put("watch", List.of(U.apply("1546868871-7041f2a55e12"), U.apply("1523275335684-37898b6baf30")));
        POOL.put("camera", List.of(U.apply("1502920917128-1aa500764cbd"), U.apply("1516035069371-29a1b244cc32")));
        POOL.put("gaming", List.of(U.apply("1486401899868-0e435ed85128"), U.apply("1587202372775-e229f172b9d7"),
                U.apply("1606813907291-d86efa9b94db")));
        POOL.put("stationery", List.of(U.apply("1531346878377-a5be20888e57")));
        POOL.put("mouse", List.of(U.apply("1527864550417-7fd91fc51a46")));
        POOL.put("monitor", List.of(U.apply("1527443224154-c4a3942d3acf")));
        POOL.put("tablet", List.of(U.apply("1544244015-0df4b3ffc6b0")));
        POOL.put("earbuds", List.of(U.apply("1590658268037-6bf12165a8df")));
        POOL.put("speaker", List.of(U.apply("1608043152269-423dbba4e7e1")));
        POOL.put("tv", List.of(U.apply("1593359677879-a4bb92f829d1")));
        POOL.put("printer", List.of(U.apply("1612815154858-60aa4c59eaa6")));
        POOL.put("router", List.of(U.apply("1606904825846-647eb07f5be2")));
        POOL.put("ssd", List.of(U.apply("1597872200969-2b65d56bd16b")));
        POOL.put("lamp", List.of(U.apply("1507473885765-e6ed057f782c")));
        POOL.put("hub", List.of(U.apply("1625723044792-44de16ccb4e9")));
        POOL.put("powerbank", List.of(U.apply("1609091839311-d5365f9ff1c5")));
        POOL.put("webcam", List.of(U.apply("1587825140708-dfaf72ae4b04")));
        POOL.put("mic", List.of(U.apply("1590602847861-f357a9332bbc")));
        POOL.put("cable", List.of(U.apply("1583863788434-e58a36330cf0")));
    }

    private final Map<String, Integer> counters = new HashMap<>();

    private static long hashStr(String s) {
        long h = 0;
        for (int c : s.toCharArray()) h = (h * 31 + c) & 0xFFFFFFFFL;
        return h;
    }

    private String imageFor(String title, String category) {
        String t = title.toLowerCase();
        String bucket = null;
        for (Rule r : RULES) {
            if (Pattern.compile(r.regex(), Pattern.CASE_INSENSITIVE).matcher(t).find()) {
                bucket = r.bucket();
                break;
            }
        }
        if (bucket == null) bucket = FALLBACK.getOrDefault(category, "powerbank");
        List<String> photos = POOL.get(bucket);
        String brand = title.split("\\s+")[0].toLowerCase();
        int start = (int) (hashStr(brand) % photos.size());
        int idx = (start + counters.merge(bucket, 1, Integer::sum) - 1) % photos.size();
        return photos.get(idx);
    }

    // ---------------------------------------------------------------- seed
    public Map<String, Object> seedAll() throws Exception {
        Random rng = new Random(7);
        OffsetDateTime now = OffsetDateTime.now();
        // wipe
        items.deleteAll();
        orders.deleteAll();
        users.deleteAll();
        mongo.getCollection("products").deleteMany(new Document());
        try {
            search.deleteIndex();
        } catch (Exception ignored) {
        }
        for (String[] u : new String[][]{
                {"John Doe", "john.doe@example.com"}, {"Jane Smith", "jane.smith@example.com"},
                {"Wendy Wireless", "wendy.wireless@example.com"}, {"Alex Rivera", "alex.rivera@example.com"},
                {"Sam Patel", "sam.patel@example.com"}, {"Casey Nguyen", "casey.nguyen@example.com"},
                {"Morgan Lee", "morgan.lee@example.com"}, {"Riley Brooks", "riley.brooks@example.com"}}) {
            User user = new User();
            user.name = u[0];
            user.email = u[1];
            users.save(user);
        }
        Map<String, double[]> prices = new LinkedHashMap<>();
        Map<String, String> titles = new LinkedHashMap<>();
        for (SeedCatalog.Prod p : SeedCatalog.catalog()) {
            byte[] bytes = new byte[12];
            rng.nextBytes(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b));
            String id = new ObjectId(hex.toString()).toHexString();
            Document doc = new Document("_id", new ObjectId(id))
                    .append("sku", p.title().split(" ")[0].toUpperCase() + "-001")
                    .append("title", p.title())
                    .append("description", p.title() + " — quality " + p.category() + " accessory.")
                    .append("price", p.price())
                    .append("category", p.category())
                    .append("tags", p.tags())
                    .append("attributes", p.attrs())
                    .append("variants", new ArrayList<>(p.variants()))
                    .append("imageUrl", imageFor(p.title(), p.category()))
                    .append("active", true)
                    .append("updated_at", LocalDateTime.now());
            mongo.getCollection("products").insertOne(doc);
            prices.put(p.key(), new double[]{p.price()});
            titles.put(p.key(), p.title());
        }
        // inactive product
        {
            byte[] bytes = new byte[12];
            rng.nextBytes(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b));
            mongo.getCollection("products").insertOne(new Document("_id", new ObjectId(hex.toString()))
                    .append("sku", "WC-000").append("title", "720p Webcam (Discontinued)")
                    .append("description", "Old model.").append("price", 29.99)
                    .append("category", "peripherals").append("tags", List.of("video"))
                    .append("attributes", Map.of("resolution", "720p"))
                    .append("variants", List.of(Map.of("sku", "WC-000", "stock", 0)))
                    .append("imageUrl", U.apply("1587825140708-dfaf72ae4b04"))
                    .append("active", false).append("updated_at", LocalDateTime.now()));
        }
        // orders: (userIdx0, status, daysAgo, [(key, qty)])
        Object[][] plan = {
                {2, "PROCESSING", 2, new Object[][]{{"WM", 1}, {"NOTEBOOK", 1}}},
                {2, "SHIPPED", 20, new Object[][]{{"WM", 1}, {"HDMI", 1}}},
                {2, "PENDING", 65, new Object[][]{{"WM", 1}, {"HDMI", 1}}},
                {0, "SHIPPED", 5, new Object[][]{{"MK", 1}}},
                {3, "PROCESSING", 12, new Object[][]{{"WM", 1}, {"MK", 1}, {"HDMI", 1}}},
                {4, "PENDING", 3, new Object[][]{{"WM", 1}, {"USBC", 1}}},
                {5, "PENDING", 40, new Object[][]{{"WM", 1}, {"MOUSEPAD", 1}}},
                {6, "SHIPPED", 70, new Object[][]{{"WM", 1}, {"DISPLAYPORT", 1}}},
                {0, "PROCESSING", 9, new Object[][]{{"MK", 2}}},
                {4, "SHIPPED", 15, new Object[][]{{"MK", 1}, {"HEADPHONES", 1}, {"USBC", 1}}},
                {1, "SHIPPED", 6, new Object[][]{{"EARBUDS", 1}, {"EARPHONES", 1}}},
                {1, "PENDING", 30, new Object[][]{{"LAMP", 1}, {"NOTEBOOK", 1}}},
                {1, "PROCESSING", 50, new Object[][]{{"HUB", 1}, {"LIGHTNING", 1}}},
                {7, "SHIPPED", 75, new Object[][]{{"HDMI", 1}, {"NOTEBOOK", 1}}},
                {7, "SHIPPED", 80, new Object[][]{{"NOTEBOOK", 2}}},
                {7, "PENDING", 66, new Object[][]{{"MOUSEPAD", 1}}},
                {3, "SHIPPED", 25, new Object[][]{{"WEBCAM", 1}, {"HDMI", 1}}},
                {3, "PENDING", 22, new Object[][]{{"STAND", 1}, {"DISPLAYPORT", 1}}},
                {5, "PENDING", 4, new Object[][]{{"CHARGER", 1}, {"LIGHTNING", 1}}},
                {5, "PENDING", 55, new Object[][]{{"USBC", 1}, {"NOTEBOOK", 1}}},
                {5, "PROCESSING", 33, new Object[][]{{"SPEAKER", 1}}},
                {6, "SHIPPED", 68, new Object[][]{{"MIC", 1}, {"USBC", 1}}},
                {6, "SHIPPED", 11, new Object[][]{{"HEADPHONES", 1}, {"EARBUDS", 1}, {"NOTEBOOK", 1}}},
                {0, "PENDING", 1, new Object[][]{{"WKEYBOARD", 1}, {"MOUSEPAD", 1}}},
                {4, "PROCESSING", 7, new Object[][]{{"HEADPHONES", 1}, {"DOCK", 1}, {"HDMI", 1}}},
                {4, "SHIPPED", 45, new Object[][]{{"MONITORLIGHT", 1}}},
                {1, "PENDING", 2, new Object[][]{{"PRESENTER", 1}, {"NOTEBOOK", 1}}},
                {4, "PROCESSING", 18, new Object[][]{{"SOUNDBAR", 1}, {"HDMI", 1}}},
                {3, "PROCESSING", 28, new Object[][]{{"ERGO", 1}, {"DISPLAYPORT", 1}}},
                {6, "PROCESSING", 38, new Object[][]{{"WEBCAMPRO", 1}, {"USBC", 1}}},
                {5, "PROCESSING", 48, new Object[][]{{"DOCK", 1}, {"HDMI", 1}}},
                {1, "PROCESSING", 58, new Object[][]{{"ORGANIZER", 1}, {"LIGHTNING", 1}}},
                {7, "PROCESSING", 72, new Object[][]{{"EARPHONES", 1}, {"NOTEBOOK", 1}}},
                {2, "PROCESSING", 16, new Object[][]{{"SPEAKER", 1}, {"PRESENTER", 1}}},
                {0, "SHIPPED", 35, new Object[][]{{"STAND", 1}, {"WM", 1}, {"MK", 1}}},
                {3, "SHIPPED", 60, new Object[][]{{"WKEYBOARD", 1}, {"DISPLAYPORT", 1}}},
                {6, "PENDING", 26, new Object[][]{{"LAMP", 1}, {"NOTEBOOK", 1}}},
                {4, "PENDING", 13, new Object[][]{{"MK", 2}, {"LAMP", 1}, {"HDMI", 1}}},
                {5, "SHIPPED", 44, new Object[][]{{"MIC", 1}, {"USBC", 1}}},
                {7, "SHIPPED", 85, new Object[][]{{"CHARGER", 1}, {"NOTEBOOK", 1}}},
        };
        Map<String, String> prodIds = new LinkedHashMap<>();
        for (Document d : mongo.getCollection("products").find()) {
            // map by title for order lines
        }
        // resolve product ids by title
        Map<String, String> titleToId = new LinkedHashMap<>();
        Map<String, Double> titleToPrice = new LinkedHashMap<>();
        for (SeedCatalog.Prod p : SeedCatalog.catalog()) {
            titleToPrice.put(p.title(), p.price());
        }
        // fetch real ids
        for (Document d : mongo.getCollection("products").find()) {
            titleToId.putIfAbsent(d.getString("title"), d.getObjectId("_id").toHexString());
        }
        int orderCount = 0;
        for (Object[] row : plan) {
            int userIdx = (int) row[0];
            String status = (String) row[1];
            int daysAgo = (int) row[2];
            Object[][] lines = (Object[][]) row[3];
            OffsetDateTime date = now.minusDays(daysAgo);
            List<OrderService.CartLine> cart = new ArrayList<>();
            // build via titles
            for (Object[] ln : lines) {
                String key = (String) ln[0];
                int qty = (int) ln[1];
                String title = titles.get(key);
                String pid = titleToId.get(title);
                cart.add(new OrderService.CartLine(pid, qty));
            }
            // place through service logic but with fixed dates: insert directly
            Order order = new Order();
            order.userId = userIdx + 1;
            order.orderDate = date;
            order.status = status;
            BigDecimal total = BigDecimal.ZERO;
            List<OrderItem> items = new ArrayList<>();
            for (OrderService.CartLine cl : cart) {
                Document p = mongo.getCollection("products")
                        .find(new Document("_id", new ObjectId(cl.productId()))).first();
                double price = ((Number) p.get("price")).doubleValue();
                total = total.add(BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(cl.quantity())));
                OrderItem item = new OrderItem();
                item.productId = cl.productId();
                item.title = (String) p.get("title");
                item.quantity = cl.quantity();
                item.unitPrice = BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);
                items.add(item);
            }
            order.totalAmount = total.setScale(2, RoundingMode.HALF_UP);
            order.updatedAt = date.plusHours(2);
            orders.save(order);
            for (OrderItem item : items) {
                item.orderId = order.id;
                this.items.save(item);
            }
            orderCount++;
        }
        // deliberate snapshot mismatch: catalog moves on, history must not
        mongo.getCollection("products").updateOne(
                new Document("title", "Wireless Mouse"),
                new Document("$set", new Document("title", "Wireless Mouse Pro").append("price", 59.99)));
        int esDocs = reindex();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", 8);
        out.put("products", 27);
        out.put("orders", orderCount);
        out.put("es_docs", esDocs);
        return out;
    }

    public int reindex() throws Exception {
        search.ensureIndex();
        List<Integer> ids = orders.findAllIds();
        List<Map<String, Object>> docs = new ArrayList<>();
        for (Integer id : ids) {
            Map<String, Object> doc = orderService.buildDoc(id);
            if (doc != null) docs.add(doc);
        }
        return search.bulkIndex(docs);
    }

    // ---- bulk demo catalog (17 e-commerce categories, BULK- skus only) ----
    public Map<String, Object> seedCatalog(int count) throws Exception {
        count = Math.min(2000, Math.max(1, count));
        Random rng = new Random(99);
        mongo.getCollection("products").deleteMany(
                new Document("sku", new Document("$regex", "^BULK-")));
        String[][] cats = {
                {"Smartphones", "Apple|Samsung|Google|OnePlus|Xiaomi", "iPhone 15|Galaxy S24|Pixel 9|13|14 Pro", "299", "1299", "storage|ram|camera_mp|battery_mah"},
                {"Laptops", "Dell|HP|Lenovo|Apple|Asus", "Inspiron 15|Pavilion 14|IdeaPad Slim|MacBook Air|VivoBook 15", "499", "2299", "ram_gb|storage_gb|screen_in|cpu"},
                {"Tablets", "Apple|Samsung|Lenovo|Xiaomi", "iPad Air|Galaxy Tab S9|Tab P12|Pad 6", "199", "999", "screen_in|storage_gb|stylus"},
                {"Headphones", "Sony|JBL|Boat|Sennheiser", "WH-1000XM5|Tune 770NC|Rockerz 550|HD 450BT", "29", "399", "anc|battery_life_h|bluetooth"},
                {"Earbuds", "Apple|Sony|Boat|Noise", "AirPods Pro|WF-1000XM5|Airdopes 141|Buds 2", "19", "249", "anc|battery_life_h|waterproof"},
                {"Smart Watches", "Apple|Samsung|Fitbit|Garmin", "Watch Series 9|Galaxy Watch 6|Versa 4|Venu 3", "99", "799", "gps|heart_rate|battery_days"},
                {"Cameras", "Canon|Nikon|Sony|Fujifilm", "EOS 1500D|D3500|Alpha 6400|X-T30", "399", "2499", "megapixels|lens_mount|video"},
                {"Monitors", "LG|Samsung|Dell|BenQ", "UltraGear 27|Odyssey G5|P2422H|GW2480", "129", "899", "size_in|refresh_hz|panel"},
                {"Keyboards", "Logitech|Zebronics|HyperX|Redgear", "K380|GK61|Alloy Elite|K509", "19", "199", "switch|backlight|wireless"},
                {"Mice", "Logitech|Razer|HP|Lenovo", "G304|DeathAdder|M221|Legion M200", "12", "149", "dpi|wireless|buttons"},
                {"Gaming", "Sony|Microsoft|Nintendo|Logitech", "DualSense|Xbox Controller|Switch Pro|G923 Wheel", "39", "549", "platform|wireless|vibration"},
                {"Accessories", "Mi|Anker|Portronics|Zebronics", "Power Bank 20000|Cable 3-in-1|Desk Mat XL|Hub 4-in-1", "9", "99", "material|warranty_mo|color"},
                {"Televisions", "Samsung|LG|Mi|TCL", "Crystal 4K 55|C1 OLED 48|A Pro 43|P635 50", "299", "1999", "size_in|resolution|smart_os"},
                {"Speakers", "JBL|Boat|Marshall|Sony", "Flip 6|Stone 120|Stone 1200|Emberton II|SRS-XB33", "29", "499", "watt|waterproof|battery_life_h"},
                {"Storage Devices", "Kingston|WD|Seagate|Sandisk", "A400 480GB|Blue 1TB|BarraCuda 2TB|Extreme 512GB", "19", "399", "capacity_gb|interface|rpm"},
                {"Networking", "TP-Link|D-Link|Netgear|Mi", "Archer C6|AX1500|RAX10|Router 4C", "19", "299", "wifi|ports|speed_mbps"},
                {"Printers", "HP|Canon|Epson|Brother", "LaserJet MFP|PIXMA G3000|EcoTank L3210|DCP-T420W", "99", "599", "duplex|wifi|tank"},
        };
        // NOTE: Speakers models include an extra entry; trim to 4 per category below.
        String[] suffixes = {"", " Pro", " Plus", " Max", " Lite", " 2024 Edition", " SE"};
        Set<String> usedTitles = new HashSet<>();
        int perCat = Math.max(1, (count + cats.length - 1) / cats.length);
        int inserted = 0, seq = 0;
        List<Document> batch = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String[] c : cats) {
            String catName = c[0];
            String[] brands = c[1].split("\\|");
            String[] models = c[2].split("\\|");
            // keep exactly 4 models per category (drop accidental extras)
            if (models.length > 4) models = Arrays.copyOf(models, 4);
            double lo = Double.parseDouble(c[3]);
            double hi = Double.parseDouble(c[4]);
            String[] attrs = c[5].split("\\|");
            for (int i = 0; i < perCat && inserted < count; i++) {
                String title = brands[rng.nextInt(brands.length)] + " " + models[rng.nextInt(models.length)]
                        + suffixes[(i / Math.max(1, brands.length)) % suffixes.length];
                if (!usedTitles.add(title)) title += " Mk" + (++seq);
                String sku = "BULK-" + catName.substring(0, 3).toUpperCase() + "-" + String.format("%04d", inserted);
                double price = Math.round((lo + rng.nextDouble() * (hi - lo)) * 100.0) / 100.0;
                String[] words = title.toLowerCase().split(" ");
                List<String> tags = new ArrayList<>();
                for (int w = 0; w < Math.min(3, words.length); w++) tags.add(words[w]);
                tags.add(catName.toLowerCase());
                Map<String, Object> am = new LinkedHashMap<>();
                for (int a = 0; a < Math.min(3, attrs.length); a++)
                    am.put(attrs[a], rng.nextInt(2) == 0
                            ? new String[]{"black", "white", "blue"}[rng.nextInt(3)]
                            : rng.nextInt(4, 512));
                batch.add(new Document("_id", new ObjectId())
                        .append("sku", sku).append("title", title)
                        .append("description", title + " — " + catName.toLowerCase() + " with " + attrs[0] + " and great value.")
                        .append("price", price).append("category", catName)
                        .append("tags", tags).append("attributes", am)
                        .append("variants", List.of(Map.of("sku", sku + "-A", "stock", 5 + rng.nextInt(95))))
                        .append("imageUrl", imageFor(title, catName))
                        .append("active", true).append("updated_at", now));
                inserted++;
            }
        }
        if (!batch.isEmpty()) mongo.getCollection("products").insertMany(batch);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inserted", inserted);
        out.put("categories", cats.length);
        out.put("totalActive", mongo.getCollection("products").countDocuments(new Document("active", true)));
        return out;
    }




}
