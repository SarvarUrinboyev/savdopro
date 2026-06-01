package uz.barakat.mobile.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.mobile.domain.Banner;
import uz.barakat.mobile.domain.Category;
import uz.barakat.mobile.domain.Product;
import uz.barakat.mobile.repository.BannerRepository;
import uz.barakat.mobile.repository.CategoryRepository;
import uz.barakat.mobile.repository.ProductRepository;

import java.util.List;

/**
 * Dev rejimida birinchi ishga tushishda namuna katalogni yuklaydi.
 * app.seed.enabled=false bo'lsa ishlamaydi. Ma'lumotlar allaqachon bo'lsa o'tkazib yuboradi.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final BannerRepository banners;

    public DataSeeder(CategoryRepository categories, ProductRepository products, BannerRepository banners) {
        this.categories = categories;
        this.products = products;
        this.banners = banners;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (categories.count() > 0) {
            log.info("Katalog allaqachon mavjud — seed o'tkazib yuborildi.");
            return;
        }
        log.info("Namuna katalog yuklanmoqda...");

        Category sut = cat("Sut mahsulotlari", "sut", 1);
        Category non = cat("Non va shirinliklar", "non", 2);
        Category ichimlik = cat("Ichimliklar", "ichimliklar", 3);
        Category mevasabzavot = cat("Meva va sabzavot", "meva-sabzavot", 4);
        Category gosht = cat("Go'sht va baliq", "gosht", 5);
        Category bakaleya = cat("Bakaleya", "bakaleya", 6);
        Category uy = cat("Uy-ro'zg'or", "uy-rozgor", 7);

        // narxlar — so'm (butun). oldPrice null bo'lsa chegirma yo'q.
        // oxirgi parametr — loremflickr rasm uchun inglizcha kalit so'z.

        // ----- Sut mahsulotlari -----
        seed(sut, "Sut 2.5% 1L", 12000L, 14000L, "dona", true, "milk");
        seed(sut, "Sut 3.2% 1L", 13000L, null, "dona", false, "milk");
        seed(sut, "Qatiq 500ml", 9000L, null, "dona", false, "yogurt");
        seed(sut, "Kefir 1L", 11000L, 13000L, "dona", true, "kefir");
        seed(sut, "Tvorog 250g", 16000L, 18000L, "dona", true, "cheese");
        seed(sut, "Smetana 20% 400g", 19000L, null, "dona", false, "cream");
        seed(sut, "Sariyog' 180g", 24000L, null, "dona", true, "butter");
        seed(sut, "Pishloq Rossiya 200g", 32000L, 36000L, "dona", false, "cheese");
        seed(sut, "Ayron 0.5L", 6000L, null, "dona", false, "yogurt");
        seed(sut, "Qaymoq 250ml", 17000L, null, "dona", false, "cream");
        seed(sut, "Muzqaymoq plombir 90g", 8000L, 9500L, "dona", true, "icecream");
        seed(sut, "Suzma 400g", 15000L, null, "dona", false, "cheese");

        // ----- Non va shirinliklar -----
        seed(non, "Oddiy non", 4000L, null, "dona", true, "bread");
        seed(non, "Buxanka non", 6500L, null, "dona", false, "bread");
        seed(non, "Lavash", 5000L, null, "dona", false, "flatbread");
        seed(non, "Pechenye 300g", 15000L, 18000L, "dona", true, "cookie");
        seed(non, "Tort shokoladli", 95000L, null, "dona", true, "cake");
        seed(non, "Shokolad plitka 90g", 14000L, 16000L, "dona", true, "chocolate");
        seed(non, "Konfet assorti 500g", 38000L, null, "dona", false, "candy");
        seed(non, "Vafli 220g", 12000L, null, "dona", false, "waffle");
        seed(non, "Keks limonli 300g", 18000L, null, "dona", false, "muffin");
        seed(non, "Pryanik 350g", 13000L, null, "dona", false, "gingerbread");
        seed(non, "Bulochka", 3500L, null, "dona", false, "bun");
        seed(non, "Halva 350g", 17000L, 19000L, "dona", false, "dessert");

        // ----- Ichimliklar -----
        seed(ichimlik, "Suv 1.5L", 4000L, null, "dona", true, "water");
        seed(ichimlik, "Gazli suv 1L", 5000L, null, "dona", false, "water");
        seed(ichimlik, "Cola 1L", 12000L, 13000L, "dona", true, "cola");
        seed(ichimlik, "Fanta 1L", 12000L, null, "dona", false, "soda");
        seed(ichimlik, "Sok olma 1L", 16000L, null, "dona", false, "juice");
        seed(ichimlik, "Sok apelsin 1L", 17000L, 19000L, "dona", true, "juice");
        seed(ichimlik, "Choy ko'k 100g", 22000L, null, "dona", false, "tea");
        seed(ichimlik, "Choy qora 100g", 21000L, null, "dona", false, "tea");
        seed(ichimlik, "Qahva 250g", 48000L, 55000L, "dona", true, "coffee");
        seed(ichimlik, "Energetik 0.5L", 11000L, null, "dona", false, "drink");
        seed(ichimlik, "Limonad 1L", 9000L, null, "dona", false, "lemonade");
        seed(ichimlik, "Mineral suv 0.5L", 3500L, null, "dona", false, "water");

        // ----- Meva va sabzavot -----
        seed(mevasabzavot, "Pomidor", 14000L, null, "kg", true, "tomato");
        seed(mevasabzavot, "Kartoshka", 7000L, 8000L, "kg", true, "potato");
        seed(mevasabzavot, "Piyoz", 6000L, null, "kg", false, "onion");
        seed(mevasabzavot, "Sabzi", 7000L, null, "kg", false, "carrot");
        seed(mevasabzavot, "Olma", 18000L, null, "kg", true, "apple");
        seed(mevasabzavot, "Banan", 24000L, 27000L, "kg", true, "banana");
        seed(mevasabzavot, "Bodring", 16000L, null, "kg", false, "cucumber");
        seed(mevasabzavot, "Uzum", 28000L, null, "kg", false, "grapes");
        seed(mevasabzavot, "Apelsin", 22000L, 25000L, "kg", false, "orange");
        seed(mevasabzavot, "Limon", 19000L, null, "kg", false, "lemon");
        seed(mevasabzavot, "Tarvuz", 6000L, null, "kg", true, "watermelon");
        seed(mevasabzavot, "Qalampir bulg'or", 21000L, null, "kg", false, "pepper");
        seed(mevasabzavot, "Karam", 5000L, null, "kg", false, "cabbage");
        seed(mevasabzavot, "Sarimsoq", 32000L, null, "kg", false, "garlic");

        // ----- Go'sht va baliq -----
        seed(gosht, "Mol go'shti", 95000L, null, "kg", true, "beef");
        seed(gosht, "Tovuq filesi", 42000L, 48000L, "kg", true, "chicken");
        seed(gosht, "Tovuq son", 36000L, null, "kg", false, "chicken");
        seed(gosht, "Qo'y go'shti", 110000L, null, "kg", false, "lamb");
        seed(gosht, "Qiyma mol 1kg", 72000L, 80000L, "kg", true, "meat");
        seed(gosht, "Baliq (forel)", 78000L, null, "kg", false, "trout");
        seed(gosht, "Baliq (skumbriya)", 39000L, null, "kg", false, "fish");
        seed(gosht, "Kolbasa pishirilgan 500g", 45000L, null, "dona", false, "sausage");
        seed(gosht, "Sosiska 500g", 38000L, 42000L, "dona", false, "sausage");
        seed(gosht, "Tuxum C1 10ta", 18000L, null, "dona", true, "eggs");
        seed(gosht, "Krevetka 300g", 65000L, null, "dona", false, "shrimp");

        // ----- Bakaleya -----
        seed(bakaleya, "Guruch lazer 1kg", 16000L, null, "dona", true, "rice");
        seed(bakaleya, "Makaron 450g", 9000L, 11000L, "dona", false, "pasta");
        seed(bakaleya, "Spagetti 500g", 11000L, null, "dona", false, "spaghetti");
        seed(bakaleya, "Yog' kungaboqar 1L", 28000L, null, "dona", true, "oil");
        seed(bakaleya, "Shakar 1kg", 13000L, null, "dona", false, "sugar");
        seed(bakaleya, "Tuz 1kg", 3000L, null, "dona", false, "salt");
        seed(bakaleya, "Un oliy nav 2kg", 18000L, 20000L, "dona", true, "flour");
        seed(bakaleya, "Grechka 800g", 19000L, null, "dona", false, "buckwheat");
        seed(bakaleya, "No'xat 900g", 15000L, null, "dona", false, "chickpeas");
        seed(bakaleya, "Mosh 900g", 17000L, null, "dona", false, "beans");
        seed(bakaleya, "Asal 500g", 55000L, 62000L, "dona", false, "honey");
        seed(bakaleya, "Tomat pastasi 500g", 14000L, null, "dona", false, "tomato");

        // ----- Uy-ro'zg'or -----
        seed(uy, "Sovun qo'l yuvish", 11000L, null, "dona", false, "soap");
        seed(uy, "Kir yuvish kukuni 1.5kg", 45000L, 52000L, "dona", true, "detergent");
        seed(uy, "Idish yuvish 500ml", 18000L, null, "dona", false, "detergent");
        seed(uy, "Salfetka 100ta", 8000L, null, "dona", false, "napkin");
        seed(uy, "Tualet qog'ozi 8ta", 22000L, 26000L, "dona", true, "tissue");
        seed(uy, "Shampun 400ml", 28000L, null, "dona", false, "shampoo");
        seed(uy, "Tish pastasi 100ml", 16000L, null, "dona", false, "toothpaste");
        seed(uy, "Gubka idish 5ta", 7000L, null, "dona", false, "sponge");
        seed(uy, "Oyna tozalagich 500ml", 19000L, null, "dona", false, "cleaning");
        seed(uy, "Axlat paketi 30ta", 12000L, null, "dona", false, "garbage");

        banner("Yangi mijozlarga -20%", "Birinchi buyurtmangizga chegirma", "/category/1", "supermarket");
        banner("Bepul yetkazib berish", "300 000 so'mdan yuqori buyurtmalarga", null, "delivery");
        banner("Mevalar yangi keldi", "Har kuni yangi meva-sabzavot", "/category/4", "fruits");

        log.info("Seed tugadi: {} kategoriya, {} mahsulot, {} banner",
                categories.count(), products.count(), banners.count());
    }

    private Category cat(String name, String slug, int sort) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setSortOrder(sort);
        return categories.save(c);
    }

    /** Rasm uchun ketma-ket "lock" — har bir rasm boshqacha bo'lishi uchun. */
    private int imgLock = 1;

    /** loremflickr orqali kalit so'zga mos (keshlanadigan) rasm URL'i. */
    private String img(String keyword) {
        return "https://loremflickr.com/400/400/" + keyword + "?lock=" + (imgLock++);
    }

    private void seed(Category cat, String name, long price, Long oldPrice, String unit,
                      boolean popular, String imgKeyword) {
        Product p = new Product();
        p.setCategory(cat);
        p.setName(name);
        p.setPrice(price);
        p.setOldPrice(oldPrice);
        p.setUnit(unit);
        p.setImageUrl(img(imgKeyword));
        p.setStockQty(100);
        p.setActive(true);
        p.setPopular(popular);
        p.setDescription(name + " — Barakat Marketdan sifatli mahsulot.");
        products.save(p);
    }

    private void banner(String title, String subtitle, String link, String imgKeyword) {
        Banner b = new Banner();
        b.setTitle(title);
        b.setSubtitle(subtitle);
        b.setActionLink(link);
        b.setImageUrl(img(imgKeyword));
        b.setSortOrder((int) banners.count());
        b.setActive(true);
        banners.save(b);
    }
}
