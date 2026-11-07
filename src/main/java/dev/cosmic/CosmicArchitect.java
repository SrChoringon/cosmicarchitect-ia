package dev.cosmic;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CosmicArchitect extends JavaPlugin implements CommandExecutor {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<UUID,Location> returns = new HashMap<>();
    private File plansFolder, registryFile;
    private YamlConfiguration registry;
    private Job active;
    private boolean checking;
    private int tickBlocks;
    private int maxWorlds;
    private long tickNanos;

    @Override public void onEnable() {
        saveDefaultConfig();
        plansFolder = new File(getDataFolder(),"plans");
        plansFolder.mkdirs();
        if (!new File(plansFolder,"eclipse.yml").exists()) saveResource("eclipse.yml",false);
        File example = new File(getDataFolder(),"eclipse.yml");
        if (example.exists() && !new File(plansFolder,"eclipse.yml").exists()) {
            try { Files.copy(example.toPath(),new File(plansFolder,"eclipse.yml").toPath()); }
            catch(IOException ex) { getLogger().log(Level.WARNING,"No se pudo copiar el plano de ejemplo",ex); }
        }
        registryFile=new File(getDataFolder(),"worlds.yml");
        registry=YamlConfiguration.loadConfiguration(registryFile);
        tickBlocks=Math.clamp(getConfig().getInt("blocks-per-tick",300),25,2000);
        maxWorlds=Math.clamp(getConfig().getInt("max-loaded-worlds",2),1,5);
        tickNanos=(long)(Math.clamp(getConfig().getDouble("milliseconds-per-tick",3),0.5,10)*1_000_000);
        Objects.requireNonNull(getCommand("cosmic")).setExecutor(this);
        getLogger().info("CosmicArchitect 0.1: constructor independiente; no registra partidas de MythicDungeons.");
    }
    @Override public void onDisable() {
        if(active!=null) stop(active,"interrupted");
        worker.shutdownNow();
    }
    private void saveRegistry() {
        try { registry.save(registryFile); }
        catch(IOException ex) { getLogger().log(Level.SEVERE,"No se pudo guardar worlds.yml",ex); }
    }
    private void reply(CommandSender sender,String text) { sender.sendMessage("[Cosmic] "+text); }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        if (!sender.hasPermission("cosmic.admin")) { reply(sender,"Necesitas cosmic.admin."); return true; }
        try {
            String action=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
            switch(action) {
                case "list" -> {
                    String[] files=plansFolder.list((d,n)->n.matches("[a-z0-9_-]{1,40}\\.yml"));
                    reply(sender,"Planos: "+(files==null?"ninguno":String.join(", ",files)));
                    Set<String> worlds=registry.getKeys(false);
                    reply(sender,"Mundos: "+(worlds.isEmpty()?"ninguno":String.join(", ",worlds.stream().map(w->w+" ["+registry.getString(w+".state","unknown")+"]").toList())));
                }
                case "check", "build" -> {
                    if(args.length!=2) throw new IllegalArgumentException("Uso: /cosmic "+action+" eclipse");
                    if(active!=null || checking) throw new IllegalArgumentException("Ya hay una tarea activa; usa /cosmic status.");
                    Architecture.Plan plan=loadPlan(args[1]);
                    boolean build=action.equals("build");
                    Job job=build?new Job(sender,plan):null;
                    if(build) active=job; else checking=true;
                    reply(sender,"Validando y calculando geometria en segundo plano...");
                    CompletableFuture.supplyAsync(()->new Architecture().generate(plan),worker).whenComplete((blocks,error)->{
                        if(!isEnabled()) return;
                        Bukkit.getScheduler().runTask(this,()->{
                            if(!build) checking=false;
                            if(build && active!=job) return;
                            if(error!=null) {
                                if(build) active=null;
                                Throwable cause=error.getCause()==null?error:error.getCause();
                                reply(sender,"Plano rechazado: "+cause.getMessage()); return;
                            }
                            if(!build) { reply(sender,"Plano valido: "+plan.rooms().size()+" salas, "+blocks.size()+" posiciones. Sin cambios en mundos."); return; }
                            try { start(job,blocks); }
                            catch(Exception ex) { fail(job,ex); }
                        });
                    });
                }
                case "status" -> {
                    if(checking) reply(sender,"Validando plano.");
                    else if(active==null) reply(sender,"Sin construcciones activas.");
                    else reply(sender,active.world==null?"Calculando plano...":active.world.getName()+": "+active.done+"/"+active.total+" bloques.");
                }
                case "cancel" -> {
                    if(active==null) throw new IllegalArgumentException("No hay una construccion activa.");
                    Job job=active; stop(job,"cancelled");
                    reply(sender,"Cancelado. "+(job.world==null?"No se creo ningun mundo.":"El mundo parcial se conserva: "+job.world.getName()));
                }
                case "visit" -> {
                    if(!(sender instanceof Player player)) throw new IllegalArgumentException("Usa este comando dentro del juego.");
                    if(args.length!=2 || !registry.isConfigurationSection(args[1]) || !"complete".equals(registry.getString(args[1]+".state")))
                        throw new IllegalArgumentException("Usa /cosmic visit <mundo completo de /cosmic list>.");
                    String name=args[1]; World world=Bukkit.getWorld(name);
                    if(world==null) { ensureCapacity(); world=new WorldCreator(name).generator(new VoidGenerator()).createWorld(); }
                    if(world==null) throw new IllegalStateException("No se pudo cargar el mundo.");
                    World target=world;
                    Location destination=new Location(target,registry.getInt(name+".x")+.5,Architecture.FLOOR+1,registry.getInt(name+".z")+.5);
                    if(!registry.isConfigurationSection(player.getWorld().getName())) returns.put(player.getUniqueId(),player.getLocation().clone());
                    player.teleportAsync(destination).thenAccept(ok->{
                        if(isEnabled()) Bukkit.getScheduler().runTask(this,()->reply(player,ok?"Bienvenido. Regresa con /cosmic leave.":"El teletransporte no se completo."));
                    });
                }
                case "leave" -> {
                    if(!(sender instanceof Player player)) throw new IllegalArgumentException("Usa este comando dentro del juego.");
                    Location back=returns.get(player.getUniqueId());
                    if(back==null || back.getWorld()==null) back=Bukkit.getWorlds().stream().filter(w->!registry.isConfigurationSection(w.getName())).findFirst().orElseThrow().getSpawnLocation();
                    player.teleportAsync(back);
                }
                case "unload" -> {
                    if(args.length!=2 || !registry.isConfigurationSection(args[1])) throw new IllegalArgumentException("Usa /cosmic unload <mundo propio>.");
                    World world=Bukkit.getWorld(args[1]);
                    if(world==null) throw new IllegalArgumentException("Ese mundo ya esta descargado.");
                    if(active!=null && active.world==world) throw new IllegalArgumentException("Primero cancela o espera la construccion.");
                    if(!world.getPlayers().isEmpty()) throw new IllegalArgumentException("Primero todos los jugadores deben salir con /cosmic leave.");
                    if(!Bukkit.unloadWorld(world,true)) throw new IllegalStateException("Paper u otro plugin impidio descargar el mundo.");
                    reply(sender,"Mundo guardado y descargado de memoria. Puedes volver a visitarlo si esta completo.");
                }
                default -> reply(sender,"/cosmic list | check <plano> | build <plano> | status | cancel | visit <mundo> | leave | unload <mundo>");
            }
        } catch(Exception ex) { reply(sender,"Error: "+ex.getMessage()); }
        return true;
    }

    private void ensureCapacity() {
        long loaded=Bukkit.getWorlds().stream().filter(w->registry.isConfigurationSection(w.getName())).count();
        if(loaded>=maxWorlds) throw new IllegalArgumentException("Limite de "+maxWorlds+" mundos propios cargados; usa /cosmic unload <mundo>.");
    }
    private Architecture.Plan loadPlan(String id) throws Exception {
        if(!id.matches("[a-z0-9_-]{1,40}")) throw new IllegalArgumentException("Nombre de archivo invalido; omite .yml.");
        Path file=new File(plansFolder,id+".yml").toPath();
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>65536) throw new IllegalArgumentException("El plano no existe o supera 64 KB.");
        YamlConfiguration yaml=new YamlConfiguration(); yaml.load(file.toFile());
        if(yaml.getInt("version")!=1) throw new IllegalArgumentException("La version del plano debe ser 1.");
        String name=yaml.getString("name",id);
        if(name.length()>100) throw new IllegalArgumentException("Nombre demasiado largo.");
        List<Architecture.Room> rooms=new ArrayList<>();
        for(Map<?,?> row:yaml.getMapList("rooms")) rooms.add(new Architecture.Room(string(row,"id"),string(row,"type"),integer(row,"x"),integer(row,"z"),integer(row,"radius"),integer(row,"height")));
        List<Architecture.Link> links=new ArrayList<>();
        for(Map<?,?> row:yaml.getMapList("links")) links.add(new Architecture.Link(string(row,"from"),string(row,"to")));
        Architecture.Plan plan=new Architecture.Plan(name,yaml.getString("palette","void"),yaml.getLong("seed",1),List.copyOf(rooms),List.copyOf(links));
        Architecture.validate(plan); return plan;
    }
    private static String string(Map<?,?> map,String key) {
        if(!(map.get(key) instanceof String value)) throw new IllegalArgumentException("Falta texto en "+key);
        return value;
    }
    private static int integer(Map<?,?> map,String key) {
        Object v=map.get(key);
        if(!(v instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()!=n.intValue()) throw new IllegalArgumentException("Se requiere entero en "+key);
        return n.intValue();
    }
    private void start(Job job,Map<Architecture.Pos,String> blocks) {
        ensureCapacity();
        String name="cosmic_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        // Never open an existing world, including one not owned by this plugin.
        if(new File(Bukkit.getWorldContainer(),name).exists()) throw new IllegalStateException("Colision de nombre de mundo; reintenta.");
        // Resolve all material data before making the world.
        blocks.values().stream().distinct().forEach(m->job.materials.put(m,Objects.requireNonNull(Material.matchMaterial(m)).createBlockData()));
        TreeMap<Long,List<Map.Entry<Architecture.Pos,String>>> chunks=new TreeMap<>();
        for(var e:blocks.entrySet()) {
            Architecture.Pos p=e.getKey();
            long key=((long)(p.x()>>4)<<32) | ((p.z()>>4)&0xffffffffL);
            chunks.computeIfAbsent(key,k->new ArrayList<>()).add(e);
        }
        job.chunks=chunks.entrySet().iterator(); job.total=blocks.size();
        WorldCreator creator=new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false);
        job.world=creator.createWorld();
        if(job.world==null) throw new IllegalStateException("No se pudo crear el mundo.");
        job.world.setDifficulty(Difficulty.PEACEFUL);
        job.world.setTime(18000);
        job.world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);
        job.world.setGameRule(GameRule.DO_WEATHER_CYCLE,false);
        job.world.setGameRule(GameRule.DO_MOB_SPAWNING,false);
        job.world.setGameRule(GameRule.DO_FIRE_TICK,false);
        job.world.setGameRule(GameRule.RANDOM_TICK_SPEED,0);
        Architecture.Room first=job.plan.rooms().getFirst();
        registry.set(name+".state","building"); registry.set(name+".title",job.plan.name());
        registry.set(name+".x",first.x()*48); registry.set(name+".z",first.z()*48); saveRegistry();
        reply(job.sender,"Construyendo "+job.plan.name()+" en "+name+" ("+job.total+" bloques). /cosmic status");
        job.timer=Bukkit.getScheduler().runTaskTimer(this,()->tick(job),1,1);
    }
    private void tick(Job job) {
        if(active!=job || job.loading) return;
        try {
            if(job.current==null || !job.current.hasNext()) {
                release(job);
                if(!job.chunks.hasNext()) { complete(job); return; }
                var next=job.chunks.next();
                int cx=(int)(next.getKey()>>32),cz=(int)(long)next.getKey();
                job.loading=true;
                job.world.getChunkAtAsync(cx,cz,true).whenComplete((chunk,error)->{
                    if(!isEnabled()) return;
                    Bukkit.getScheduler().runTask(this,()->{
                        if(active!=job) return;
                        if(error!=null) { fail(job,error); return; }
                        chunk.addPluginChunkTicket(this); job.held=chunk;
                        job.current=next.getValue().iterator(); job.loading=false;
                    });
                });
                return;
            }
            long start=System.nanoTime(); int count=0;
            while(job.current.hasNext() && count<tickBlocks && System.nanoTime()-start<tickNanos) {
                var e=job.current.next(); var p=e.getKey();
                job.world.getBlockAt(p.x(),p.y(),p.z()).setBlockData(job.materials.get(e.getValue()),false);
                count++; job.done++;
            }
        } catch(Exception ex) { fail(job,ex); }
    }
    private void release(Job job) {
        if(job.held!=null) {
            int x=job.held.getX(),z=job.held.getZ();
            job.held.removePluginChunkTicket(this); job.held=null;
            job.world.unloadChunkRequest(x,z);
        }
    }
    private void complete(Job job) {
        String name=job.world.getName();
        Architecture.Room first=job.plan.rooms().getFirst();
        job.world.setSpawnLocation(first.x()*48,Architecture.FLOOR+1,first.z()*48);
        stop(job,"complete");
        reply(job.sender,"Listo: "+job.done+" bloques. /cosmic visit "+name);
    }
    private void stop(Job job,String state) {
        if(job.timer!=null) job.timer.cancel();
        release(job);
        if(job.world!=null) { registry.set(job.world.getName()+".state",state); saveRegistry(); }
        if(active==job) active=null;
    }
    private void fail(Job job,Throwable error) {
        stop(job,"failed"); reply(job.sender,"Construccion detenida: "+error.getMessage()+". El mundo parcial se conserva.");
        getLogger().log(Level.WARNING,"Construccion fallida",error);
    }
    private static final class Job {
        final CommandSender sender; final Architecture.Plan plan;
        final Map<String,BlockData> materials=new HashMap<>();
        World world; Chunk held; BukkitTask timer; boolean loading;
        int total,done;
        Iterator<Map.Entry<Long,List<Map.Entry<Architecture.Pos,String>>>> chunks;
        Iterator<Map.Entry<Architecture.Pos,String>> current;
        Job(CommandSender sender,Architecture.Plan plan) { this.sender=sender; this.plan=plan; }
    }
    public static final class VoidGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        @Override public Location getFixedSpawnLocation(World world,Random random) { return new Location(world,0,Architecture.FLOOR+1,0); }
    }
}
