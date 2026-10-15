package pl.skyrynek;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.stream.*;

public final class SkyRynek extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
 private Economy eco; private Connection db; private YamlConfiguration messages;
 private final Map<UUID, Screen> screens = new HashMap<>();
 private record Auction(long id, UUID seller, String sellerName, ItemStack item, double price, long expires) {}
 private record Screen(String type, String filter, String sort, int page, long selected) {}
 private static final int[] LIST_SLOTS = IntStream.range(0,45).filter(i->i%9!=0).toArray();
 private final String[] sorts={"PRICE_ASC","PRICE_DESC","NEWEST","OLDEST"};
 @Override public void onEnable(){
  saveDefaultConfig(); loadMessages();
  try { Class.forName("org.sqlite.JDBC"); db=DriverManager.getConnection("jdbc:sqlite:"+new File(getDataFolder(),"auctions.db").getAbsolutePath());
   try(Statement s=db.createStatement()){
    s.executeUpdate("CREATE TABLE IF NOT EXISTS auctions(id INTEGER PRIMARY KEY AUTOINCREMENT,seller TEXT NOT NULL,seller_name TEXT NOT NULL,item TEXT NOT NULL,price REAL NOT NULL,expires INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'ACTIVE')");
    s.executeUpdate("CREATE TABLE IF NOT EXISTS sales(id INTEGER PRIMARY KEY AUTOINCREMENT,seller TEXT,buyer TEXT,item TEXT,price REAL,time INTEGER)");
    s.executeUpdate("CREATE TABLE IF NOT EXISTS payouts(id INTEGER PRIMARY KEY AUTOINCREMENT,player TEXT NOT NULL,amount REAL NOT NULL,delivered INTEGER NOT NULL DEFAULT 0)");
   }
  } catch(Exception e){getLogger().severe("SQLite: "+e.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
  if(getServer().getPluginManager().getPlugin("Vault")!=null){RegisteredServiceProvider<Economy> rsp=getServer().getServicesManager().getRegistration(Economy.class);if(rsp!=null)eco=rsp.getProvider();}
  if(eco==null)getLogger().warning("Nie znaleziono dostawcy ekonomii Vault! Zakupy i wystawianie nie beda dzialac.");
  for(String name:List.of("ah","rynek")){PluginCommand c=getCommand(name);if(c!=null){c.setExecutor(this);c.setTabCompleter(this);}}
  getServer().getPluginManager().registerEvents(this,this);
  getServer().getScheduler().runTaskTimer(this,()->{try{expire();}catch(Exception e){getLogger().warning(e.getMessage());}},20L,1200L);
 }
 @Override public void onDisable(){try{if(db!=null)db.close();}catch(Exception ignored){}}
 private void loadMessages(){File f=new File(getDataFolder(),"messages.yml");if(!f.exists())saveResource("messages.yml",false);messages=YamlConfiguration.loadConfiguration(f);}
 private String color(String s){return ChatColor.translateAlternateColorCodes('&',s);}
 private void msg(Player p,String s){p.sendMessage(color("&8[&dSkyRynek&8] &7"+s));}
 private String encode(ItemStack item)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(BukkitObjectOutputStream os=new BukkitObjectOutputStream(out)){os.writeObject(item);}return Base64.getEncoder().encodeToString(out.toByteArray());}
 private ItemStack decode(String value)throws Exception{try(BukkitObjectInputStream in=new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)))){return (ItemStack)in.readObject();}}
 private List<Auction> auctions(String status){List<Auction> result=new ArrayList<>();try(PreparedStatement p=db.prepareStatement("SELECT * FROM auctions WHERE status=?")){p.setString(1,status);try(ResultSet r=p.executeQuery()){while(r.next())result.add(new Auction(r.getLong("id"),UUID.fromString(r.getString("seller")),r.getString("seller_name"),decode(r.getString("item")),r.getDouble("price"),r.getLong("expires")));}}catch(Exception e){getLogger().warning("Odczyt: "+e.getMessage());}return result;}
 private Auction find(long id,String status){return auctions(status).stream().filter(a->a.id()==id).findFirst().orElse(null);}
 private void expire()throws SQLException{try(PreparedStatement p=db.prepareStatement("UPDATE auctions SET status='EXPIRED' WHERE status='ACTIVE' AND expires<=?")){p.setLong(1,System.currentTimeMillis());p.executeUpdate();}}
 private int limit(Player p){if(p.hasPermission("skyrynek.limit.15"))return 15;if(p.hasPermission("skyrynek.limit.10"))return 10;if(p.hasPermission("skyrynek.limit.6"))return 6;return getConfig().getInt("limits.default",3);}
 private int count(UUID u){return (int)auctions("ACTIVE").stream().filter(a->a.seller().equals(u)).count();}
 private void sell(Player p,String amount){if(eco==null){msg(p,"&cBrak dostawcy ekonomii Vault.");return;}double price;try{price=Double.parseDouble(amount);}catch(Exception e){msg(p,"&cPodaj poprawna cene.");return;}
  if(!Double.isFinite(price)||price<getConfig().getDouble("min-price",1)||price>getConfig().getDouble("max-price",1e9)){msg(p,"&cNieprawidlowa cena.");return;}
  ItemStack hand=p.getInventory().getItemInMainHand();if(hand.getType().isAir()){msg(p,"&cTrzymaj przedmiot w rece.");return;}
  if(count(p.getUniqueId())>=limit(p)){msg(p,"&cOsiagnieto limit aukcji: "+limit(p));return;}
  try(PreparedStatement s=db.prepareStatement("INSERT INTO auctions(seller,seller_name,item,price,expires,status) VALUES(?,?,?,?,?,'ACTIVE')")){
   s.setString(1,p.getUniqueId().toString());s.setString(2,p.getName());s.setString(3,encode(hand.clone()));s.setDouble(4,price);s.setLong(5,System.currentTimeMillis()+getConfig().getLong("auction-hours",24)*3600000L);s.executeUpdate();p.getInventory().setItemInMainHand(null);msg(p,"&aWystawiono za &e"+price+"$&a.");
  }catch(Exception e){msg(p,"&cBlad zapisu aukcji.");getLogger().warning(e.getMessage());}
 }
 private ItemStack button(Material material,String name,String... lore){ItemStack i=new ItemStack(material);ItemMeta m=i.getItemMeta();m.setDisplayName(color(name));m.setLore(Arrays.stream(lore).map(this::color).toList());i.setItemMeta(m);return i;}
 private void open(Player p,String type,String filter,String sort,int page,long selected){expireQuiet();List<Auction> list=filtered(p,type,filter,sort);int pages=Math.max(1,(list.size()+LIST_SLOTS.length-1)/LIST_SLOTS.length);page=Math.max(1,Math.min(page,pages));
  if(type.equals("MENU")){Inventory inv=Bukkit.createInventory(null,27,"SkyRynek | Menu");inv.setItem(11,button(Material.CHEST,"&aOpen / Otworz"));inv.setItem(13,button(Material.COMPASS,"&eSearch / Szukaj"));inv.setItem(15,button(Material.EMERALD,"&bSell / Sprzedaj","&7/ah sell <cena>","&7/rynek sprzedaj <cena>"));p.openInventory(inv);screens.put(p.getUniqueId(),new Screen(type,filter,sort,1,0));return;}
  if(type.equals("CONFIRM")){Auction a=find(selected,"ACTIVE");if(a==null){msg(p,"&cAukcja juz nie istnieje.");return;}Inventory inv=Bukkit.createInventory(null,27,"SkyRynek | Potwierdz zakup");inv.setItem(13,a.item());inv.setItem(11,button(Material.LIME_WOOL,"&aKup za "+a.price()+"$"));inv.setItem(15,button(Material.RED_WOOL,"&cAnuluj"));p.openInventory(inv);screens.put(p.getUniqueId(),new Screen(type,filter,sort,page,selected));return;}
  Inventory inv=Bukkit.createInventory(null,54,"SkyRynek | "+(type.equals("MY")?"Moje":type.equals("EXPIRED")?"Wygasle":type.equals("COLLECT")?"Odbierz":"Rynek"));
  inv.setItem(0,button(Material.CHEST,"&eWszystkie"));inv.setItem(9,button(Material.DIAMOND_PICKAXE,"&bNarzedzia"));inv.setItem(18,button(Material.DIAMOND_CHESTPLATE,"&bZbroje"));inv.setItem(27,button(Material.TRIPWIRE_HOOK,"&6Klucze"));inv.setItem(36,button(Material.NETHER_STAR,"&dSpecjalne"));
  int start=(page-1)*LIST_SLOTS.length;
  for(int j=0;j<LIST_SLOTS.length&&start+j<list.size();j++){Auction a=list.get(start+j);ItemStack item=a.item().clone();ItemMeta meta=item.getItemMeta();List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();lore.add(color("&8------------------"));lore.add(color("&eCena: &f"+a.price()+"$"));lore.add(color("&7Sprzedawca: &f"+a.sellerName()));lore.add(color("&7ID: &f"+a.id()));lore.add(color("&7Wygasa: &f"+Math.max(0,(a.expires()-System.currentTimeMillis())/60000)+" min"));lore.add(color("&aKliknij, aby "+(type.equals("EXPIRED")||type.equals("COLLECT")?"odebrac":type.equals("MY")?"anulowac":"kupic")));meta.setLore(lore);item.setItemMeta(meta);inv.setItem(LIST_SLOTS[j],item);}
  inv.setItem(45,button(Material.PLAYER_HEAD,"&aTwoje aukcje","&7"+count(p.getUniqueId())+"/"+limit(p)));
  inv.setItem(46,button(Material.CLOCK,"&eWygasle przedmioty"));
  if(page>1)inv.setItem(47,button(Material.ARROW,"&bPoprzednia strona"));
  inv.setItem(48,button(Material.PAPER,"&fStrona "+page+"/"+pages));inv.setItem(49,button(Material.BARRIER,"&cZamknij"));
  inv.setItem(50,button(Material.HOPPER,"&eOdbierz zwroty"));
  if(page<pages)inv.setItem(51,button(Material.ARROW,"&aNastepna strona"));
  inv.setItem(52,button(Material.COMPARATOR,"&dSortowanie: &f"+sort));inv.setItem(53,button(Material.SUNFLOWER,"&aOdswiez"));
  p.openInventory(inv);screens.put(p.getUniqueId(),new Screen(type,filter,sort,page,0));
 }
 private void expireQuiet(){try{expire();}catch(Exception e){getLogger().warning(e.getMessage());}}
 private boolean category(ItemStack i,String filter){Material m=i.getType();String n=m.name();return switch(filter){case "TOOLS"->n.endsWith("_SWORD")||n.endsWith("_PICKAXE")||n.endsWith("_AXE")||n.endsWith("_SHOVEL")||n.endsWith("_HOE")||n.equals("BOW")||n.equals("CROSSBOW");case "ARMOR"->n.endsWith("_HELMET")||n.endsWith("_CHESTPLATE")||n.endsWith("_LEGGINGS")||n.endsWith("_BOOTS")||n.equals("ELYTRA")||n.equals("SHIELD");case "KEYS"->m==Material.TRIPWIRE_HOOK||m==Material.NAME_TAG;case "SPECIAL"->!category(i,"TOOLS")&&!category(i,"ARMOR")&&!category(i,"KEYS");default->true;};}
 private List<Auction> filtered(Player p,String type,String filter,String sort){List<Auction> l=new ArrayList<>(auctions(type.equals("EXPIRED")||type.equals("COLLECT")?"EXPIRED":"ACTIVE"));if(type.equals("MY")||type.equals("EXPIRED")||type.equals("COLLECT"))l.removeIf(a->!a.seller().equals(p.getUniqueId()));else if(type.equals("PLAYER"))l.removeIf(a->!a.sellerName().equalsIgnoreCase(filter));else if(type.equals("SEARCH"))l.removeIf(a->!a.item().getType().name().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))&&!(a.item().hasItemMeta()&&a.item().getItemMeta().hasDisplayName()&&ChatColor.stripColor(a.item().getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))));else l.removeIf(a->!category(a.item(),filter));
  Comparator<Auction> c=switch(sort){case "PRICE_DESC"->Comparator.comparingDouble(Auction::price).reversed();case "NEWEST"->Comparator.comparingLong(Auction::id).reversed();case "OLDEST"->Comparator.comparingLong(Auction::id);default->Comparator.comparingDouble(Auction::price);};l.sort(c.thenComparingLong(Auction::id));return l;}
 @EventHandler public void onClick(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;Screen s=screens.get(p.getUniqueId());if(s==null)return;if(!e.getView().getTopInventory().equals(e.getClickedInventory())&&e.getClickedInventory()!=null){if(e.isShiftClick())e.setCancelled(true);return;}e.setCancelled(true);if(e.getClickedInventory()==null)return;int slot=e.getRawSlot();
  if(s.type().equals("MENU")){if(slot==11)open(p,"MARKET","ALL","PRICE_ASC",1,0);else if(slot==13)msg(p,"&eUzyj /ah search <nazwa> lub /rynek szukaj <nazwa>");else if(slot==15)msg(p,"&eTrzymaj przedmiot i uzyj /ah sell <cena>.");return;}
  if(s.type().equals("CONFIRM")){if(slot==11)buy(p,s.selected());else if(slot==15)open(p,"MARKET","ALL",s.sort(),s.page(),0);return;}
  if(slot==0||slot==9||slot==18||slot==27||slot==36){open(p,"MARKET",switch(slot){case 9->"TOOLS";case 18->"ARMOR";case 27->"KEYS";case 36->"SPECIAL";default->"ALL";},s.sort(),1,0);return;}
  if(slot==45){open(p,"MY","ALL",s.sort(),1,0);return;}if(slot==46){open(p,"EXPIRED","ALL",s.sort(),1,0);return;}if(slot==47){open(p,s.type(),s.filter(),s.sort(),s.page()-1,0);return;}if(slot==49){p.closeInventory();return;}if(slot==50){open(p,"COLLECT","ALL",s.sort(),1,0);return;}if(slot==51){open(p,s.type(),s.filter(),s.sort(),s.page()+1,0);return;}if(slot==52){int i=Arrays.asList(sorts).indexOf(s.sort());open(p,s.type(),s.filter(),sorts[(i+1)%sorts.length],1,0);return;}if(slot==53){open(p,s.type(),s.filter(),s.sort(),s.page(),0);return;}
  int idx=Arrays.stream(LIST_SLOTS).boxed().toList().indexOf(slot);if(idx<0)return;List<Auction> list=filtered(p,s.type(),s.filter(),s.sort());int at=(s.page()-1)*LIST_SLOTS.length+idx;if(at>=list.size())return;Auction a=list.get(at);
  if(s.type().equals("MY")){cancel(p,a.id(),false);open(p,"MY","ALL",s.sort(),s.page(),0);}else if(s.type().equals("EXPIRED")||s.type().equals("COLLECT")){collect(p,a.id());open(p,s.type(),s.filter(),s.sort(),s.page(),0);}else open(p,"CONFIRM",s.filter(),s.sort(),s.page(),a.id());
 }
 @EventHandler public void onDrag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p&&screens.containsKey(p.getUniqueId())&&e.getRawSlots().stream().anyMatch(i->i<e.getView().getTopInventory().getSize()))e.setCancelled(true);}
 @EventHandler public void onClose(InventoryCloseEvent e){if(e.getPlayer() instanceof Player p)screens.remove(p.getUniqueId());}
 private void buy(Player buyer,long id){if(eco==null){msg(buyer,"&cBrak ekonomii Vault.");return;}Auction a=find(id,"ACTIVE");if(a==null||a.expires()<=System.currentTimeMillis()){msg(buyer,"&cAukcja jest niedostepna.");return;}if(a.seller().equals(buyer.getUniqueId())){msg(buyer,"&cNie mozesz kupic swojej aukcji.");return;}
  if(buyer.getInventory().firstEmpty()<0){msg(buyer,"&cZrob miejsce w ekwipunku przed zakupem.");return;}if(!eco.has(buyer,a.price())){msg(buyer,"&cNie masz wystarczajaco pieniedzy.");return;}
  try{db.setAutoCommit(false);try(PreparedStatement q=db.prepareStatement("UPDATE auctions SET status='SOLD' WHERE id=? AND status='ACTIVE'")){q.setLong(1,id);if(q.executeUpdate()!=1){db.rollback();msg(buyer,"&cAukcja zostala juz kupiona.");return;}}
   if(!eco.withdrawPlayer(buyer,a.price()).transactionSuccess()){db.rollback();msg(buyer,"&cPlatnosc nie powiodla sie.");return;}
   try(PreparedStatement q=db.prepareStatement("INSERT INTO sales(seller,buyer,item,price,time) VALUES(?,?,?,?,?)")){q.setString(1,a.seller().toString());q.setString(2,buyer.getUniqueId().toString());q.setString(3,a.item().getType().name());q.setDouble(4,a.price());q.setLong(5,System.currentTimeMillis());q.executeUpdate();}
   try(PreparedStatement q=db.prepareStatement("INSERT INTO payouts(player,amount,delivered) VALUES(?,?,0)")){q.setString(1,a.seller().toString());q.setDouble(2,a.price());q.executeUpdate();}db.commit();
   buyer.getInventory().addItem(a.item().clone());title(buyer,"buy-title","buy-subtitle",a.price());msg(buyer,"&aKupiono przedmiot.");
   Player seller=Bukkit.getPlayer(a.seller());if(seller!=null){payPending(seller);title(seller,"sale-title","sale-subtitle",a.price());}
   buyer.closeInventory();
  }catch(Exception ex){try{db.rollback();}catch(Exception ignored){}msg(buyer,"&cBlad transakcji. Zglos administracji.");getLogger().severe("Transakcja "+id+": "+ex.getMessage());}finally{try{db.setAutoCommit(true);}catch(Exception ignored){}}
 }
 private void title(Player p,String titleKey,String subKey,double amount){String price=String.format(Locale.US,"%.2f",amount);p.sendTitle(color(messages.getString(titleKey,"").replace("%price%",price)),color(messages.getString(subKey,"").replace("%price%",price)),10,60,10);}
 @EventHandler public void onJoin(org.bukkit.event.player.PlayerJoinEvent e){getServer().getScheduler().runTaskLater(this,()->payPending(e.getPlayer()),20L);}
 private void payPending(Player p){if(eco==null)return;try(PreparedStatement q=db.prepareStatement("SELECT id,amount FROM payouts WHERE player=? AND delivered=0")){q.setString(1,p.getUniqueId().toString());try(ResultSet r=q.executeQuery()){while(r.next()){long id=r.getLong(1);double amount=r.getDouble(2);if(eco.depositPlayer(p,amount).transactionSuccess()){try(PreparedStatement u=db.prepareStatement("UPDATE payouts SET delivered=1 WHERE id=? AND delivered=0")){u.setLong(1,id);u.executeUpdate();}msg(p,"&aOtrzymano &e"+amount+"$ &aza sprzedana aukcje.");}}}}catch(Exception ex){getLogger().warning("Wyplata: "+ex.getMessage());}}
 private void cancel(Player p,long id,boolean admin){Auction a=find(id,"ACTIVE");if(a==null){msg(p,"&cNie ma takiej aukcji.");return;}if(!admin&&!a.seller().equals(p.getUniqueId())){msg(p,"&cTo nie twoja aukcja.");return;}try(PreparedStatement q=db.prepareStatement("UPDATE auctions SET status='EXPIRED' WHERE id=? AND status='ACTIVE'")){q.setLong(1,id);q.executeUpdate();msg(p,"&aPrzedmiot przeniesiono do wygaslych / do odbioru.");}catch(Exception e){msg(p,"&cBlad anulowania.");}}
 private void collect(Player p,long id){Auction a=find(id,"EXPIRED");if(a==null||!a.seller().equals(p.getUniqueId())){msg(p,"&cBrak przedmiotu.");return;}if(p.getInventory().firstEmpty()<0){msg(p,"&cBrak miejsca w ekwipunku.");return;}try(PreparedStatement q=db.prepareStatement("UPDATE auctions SET status='COLLECTED' WHERE id=? AND status='EXPIRED'")){q.setLong(1,id);if(q.executeUpdate()==1){p.getInventory().addItem(a.item().clone());msg(p,"&aOdebrano przedmiot.");}}catch(Exception e){msg(p,"&cBlad odbioru.");}}
 @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){if(!(sender instanceof Player p)){sender.sendMessage("Komendy gracza wymagaja wejscia na serwer.");return true;}if(!p.hasPermission("skyrynek.use")){msg(p,"&cBrak uprawnien.");return true;}boolean pl=cmd.getName().equalsIgnoreCase("rynek");if(args.length==0){open(p,"MENU","ALL","PRICE_ASC",1,0);return true;}
  String sub=args[0].toLowerCase(Locale.ROOT);if(pl)sub=switch(sub){case "otworz"->"open";case "sprzedaj"->"sell";case "szukaj"->"search";case "moje"->"my";case "wygasle"->"expired";case "odbierz"->"collect";case "historia"->"history";case "gracz"->"player";case "anuluj"->"cancel";case "pomoc"->"help";case "usun"->"remove";case "przeladuj"->"reload";default->sub;};
  switch(sub){case "open"->open(p,"MARKET","ALL","PRICE_ASC",1,0);case "sell"->{if(args.length<2)msg(p,"&e/"+label+" "+(pl?"sprzedaj":"sell")+" <cena>");else sell(p,args[1]);}
   case "search"->{if(args.length<2)msg(p,"&ePodaj nazwe przedmiotu.");else open(p,"SEARCH",String.join(" ",Arrays.copyOfRange(args,1,args.length)),"PRICE_ASC",1,0);}
   case "my"->open(p,"MY","ALL","PRICE_ASC",1,0);case "expired"->open(p,"EXPIRED","ALL","PRICE_ASC",1,0);case "collect"->open(p,"COLLECT","ALL","PRICE_ASC",1,0);
   case "player"->{if(args.length<2)msg(p,"&ePodaj nick.");else open(p,"PLAYER",args[1],"PRICE_ASC",1,0);}
   case "cancel"->{if(args.length<2)msg(p,"&ePodaj ID aukcji.");else tryId(p,args[1],id->cancel(p,id,false));}
   case "remove"->{if(!p.hasPermission("skyrynek.admin"))msg(p,"&cBrak uprawnien.");else if(args.length<2)msg(p,"&ePodaj ID.");else tryId(p,args[1],id->cancel(p,id,true));}
   case "history"->{try(PreparedStatement q=db.prepareStatement("SELECT item,price FROM sales WHERE seller=? OR buyer=? ORDER BY id DESC LIMIT 10")){q.setString(1,p.getUniqueId().toString());q.setString(2,p.getUniqueId().toString());try(ResultSet r=q.executeQuery()){msg(p,"&eOstatnie transakcje:");while(r.next())msg(p,"&f"+r.getString(1)+" &7- &e"+r.getDouble(2)+"$");}}catch(Exception e){msg(p,"&cBlad historii.");}}
   case "admin"->{if(p.hasPermission("skyrynek.admin"))msg(p,"&eAdmin: /ah remove <id>, /ah reload, /ah player <nick>");else msg(p,"&cBrak uprawnien.");}
   case "reload"->{if(p.hasPermission("skyrynek.admin")){reloadConfig();loadMessages();msg(p,"&aPrzeladowano konfiguracje.");}else msg(p,"&cBrak uprawnien.");}
   case "help"->{msg(p,"&e/"+label+" open, sell, search, my, expired, collect, history, player, cancel");}
   default->msg(p,"&cNieznana komenda. /"+label+" "+(pl?"pomoc":"help"));
  }return true;
 }
 private void tryId(Player p,String value,java.util.function.LongConsumer consumer){try{consumer.accept(Long.parseLong(value));}catch(NumberFormatException e){msg(p,"&cID musi byc liczba.");}}
 @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){boolean pl=command.getName().equalsIgnoreCase("rynek");List<String> words=pl?List.of("otworz","sprzedaj","szukaj","moje","wygasle","odbierz","historia","gracz","anuluj","pomoc","admin","usun","przeladuj"):List.of("open","sell","search","my","expired","collect","history","player","cancel","help","admin","remove","reload");if(args.length==1)return words.stream().filter(w->w.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();return List.of();}
}
