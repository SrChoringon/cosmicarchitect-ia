package dev.cosmic;

import java.util.*;

/** Pure, deterministic geometry; no server API or arbitrary code from plans. */
public final class Architecture {
    public record Room(String id, String kind, int x, int z, int radius, int height) {}
    public record Link(String from, String to) {}
    public record Plan(String name, String palette, long seed, List<Room> rooms, List<Link> links) {}
    public record Pos(int x, int y, int z) {}
    public static final int FLOOR = 80, MAX_BLOCKS = 250_000;
    private final LinkedHashMap<Pos, String> blocks = new LinkedHashMap<>();
    private String base, trim, glass, light;
    private long seed;

    public static void validate(Plan p) {
        if (p.rooms().isEmpty() || p.rooms().size() > 10) throw new IllegalArgumentException("Usa entre 1 y 10 salas.");
        if (!Set.of("void", "nebula", "eclipse").contains(p.palette())) throw new IllegalArgumentException("Paleta: void, nebula o eclipse.");
        Map<String, Room> ids = new HashMap<>();
        Set<String> cells = new HashSet<>();
        for (Room r : p.rooms()) {
            if (!r.id().matches("[a-z0-9_-]{1,32}") || ids.put(r.id(), r) != null) throw new IllegalArgumentException("ID de sala invalido o repetido.");
            if (!Set.of("observatory", "library", "sanctum", "arena", "gateway").contains(r.kind())) throw new IllegalArgumentException("Tipo de sala desconocido: " + r.kind());
            if (Math.abs((long)r.x()) > 3 || Math.abs((long)r.z()) > 3 || !cells.add(r.x()+":"+r.z())) throw new IllegalArgumentException("Celdas repetidas o fuera de -3..3.");
            if (r.radius() < 9 || r.radius() > 16 || r.height() < 10 || r.height() > 20) throw new IllegalArgumentException("Radio: 9..16; altura: 10..20.");
        }
        if (p.links().size() > 20) throw new IllegalArgumentException("Maximo 20 conexiones.");
        Map<String, Set<String>> graph = new HashMap<>();
        ids.keySet().forEach(id -> graph.put(id, new HashSet<>()));
        for (Link l : p.links()) {
            Room a = ids.get(l.from()), b = ids.get(l.to());
            if (a == null || b == null || a == b) throw new IllegalArgumentException("Conexion invalida.");
            // Adjacent grid cells give straight corridors without accidental crossings.
            if (Math.abs(a.x()-b.x()) + Math.abs(a.z()-b.z()) != 1) throw new IllegalArgumentException("Conecta solo celdas vecinas (distancia 1).");
            if (!graph.get(a.id()).add(b.id())) throw new IllegalArgumentException("Conexion repetida.");
            graph.get(b.id()).add(a.id());
        }
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(); queue.add(p.rooms().getFirst().id());
        while (!queue.isEmpty()) { String id = queue.remove(); if (seen.add(id)) queue.addAll(graph.get(id)); }
        if (seen.size() != ids.size()) throw new IllegalArgumentException("Hay salas desconectadas de la entrada.");
    }

    public Map<Pos, String> generate(Plan p) {
        validate(p); blocks.clear(); seed = p.seed();
        switch (p.palette()) {
            case "nebula" -> { base="PURPUR_BLOCK"; trim="POLISHED_DEEPSLATE"; glass="MAGENTA_STAINED_GLASS"; light="SEA_LANTERN"; }
            case "eclipse" -> { base="POLISHED_BLACKSTONE_BRICKS"; trim="GILDED_BLACKSTONE"; glass="ORANGE_STAINED_GLASS"; light="SHROOMLIGHT"; }
            default -> { base="DEEPSLATE_TILES"; trim="POLISHED_BLACKSTONE_BRICKS"; glass="PURPLE_STAINED_GLASS"; light="SEA_LANTERN"; }
        }
        Map<String, Room> ids = new HashMap<>();
        for (Room r : p.rooms()) { ids.put(r.id(), r); room(r); }
        // Carve corridors after rooms to guarantee open doorways.
        for (Link l : p.links()) corridor(ids.get(l.from()), ids.get(l.to()));
        // Central spawn is always clear, including decorative archetypes.
        Room start = p.rooms().getFirst();
        for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) for (int y=1;y<=4;y++) put(start.x()*48+x,y,start.z()*48+z,"AIR");
        return Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
    }
    private void put(int x,int y,int z,String material) {
        blocks.put(new Pos(x,FLOOR+y,z),material);
        if (blocks.size()>MAX_BLOCKS) throw new IllegalArgumentException("El plano supera 250000 bloques; reduce salas, radios o alturas.");
    }
    private void room(Room r) {
        int cx=r.x()*48, cz=r.z()*48, radius=r.radius(), h=r.height();
        boolean square=r.kind().equals("library") || r.kind().equals("gateway");
        for (int x=-radius;x<=radius;x++) for (int z=-radius;z<=radius;z++) {
            double d=square?Math.max(Math.abs(x),Math.abs(z)):Math.sqrt(x*x+z*z);
            if (d>radius) continue;
            boolean ring=d>radius-2 || (d>radius*.55 && d<radius*.55+1);
            String floor=ring?trim:base;
            if ((x==0 || z==0) && Math.abs(x)+Math.abs(z)>3) floor="AMETHYST_BLOCK";
            if (Math.floorMod(Objects.hash(x,z,seed),43)==0) floor=light;
            put(cx+x,0,cz+z,floor); put(cx+x,-1,cz+z,trim);
            int ceiling=square?h:(int)Math.round(h*.55 + h*.45*Math.sqrt(Math.max(0,1-d*d/(radius*radius))));
            boolean edge=d>radius-1.2;
            if (edge) for (int y=1;y<ceiling;y++) put(cx+x,y,cz+z,y>3 && y<ceiling-1?glass:trim);
            put(cx+x,ceiling,cz+z,Math.floorMod(Objects.hash(x,z,seed),29)==0?light:glass);
            if (!edge && Math.abs(x)>3 && Math.abs(z)>3 && Math.abs(x)%7==0 && Math.abs(z)%7==0) {
                for (int y=1;y<ceiling;y++) put(cx+x,y,cz+z,y%5==0?light:"POLISHED_BASALT");
            }
        }
        switch(r.kind()) {
            case "observatory" -> orbit(cx,cz,6,7,false);
            case "arena" -> { orbit(cx,cz,8,8,true); pedestal(cx,cz,2); }
            case "sanctum" -> { pedestal(cx,cz,3); orbit(cx,cz,5,7,false); }
            case "library" -> {
                for (int x=-radius+3;x<=radius-3;x+=4) for (int z=-radius+3;z<=radius-3;z++)
                    if (Math.abs(x)>3 && Math.abs(z)>4) for (int y=1;y<=4;y++) put(cx+x,y,cz+z,y==4?trim:"BOOKSHELF");
                orbit(cx,cz,4,7,false);
            }
            case "gateway" -> {
                for (int x=-4;x<=4;x++) for(int y=1;y<=8;y++) if(Math.abs(x)==4 || y==8)
                    put(cx+x,y,cz+5,y==8?light:"CRYING_OBSIDIAN");
            }
        }
        // Four luminous obelisks leave cardinal routes unobstructed.
        int offset=(int)(radius*.52);
        for (int sx : new int[]{-1,1}) for(int sz:new int[]{-1,1}) {
            for(int y=1;y<=4;y++) put(cx+sx*offset,y,cz+sz*offset,y==4?light:"AMETHYST_BLOCK");
        }
    }
    private void pedestal(int cx,int cz,int radius) {
        // Cosmetic altar at the back; center and cardinal paths stay walkable.
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) if(x*x+z*z<=radius*radius)
            put(cx+x,1,cz+z+6,trim);
        put(cx,2,cz+6,"AMETHYST_BLOCK"); put(cx,3,cz+6,light);
    }
    private void orbit(int cx,int cz,int radius,int y,boolean tilted) {
        for(int i=0;i<240;i++) {
            double a=i*Math.PI/120;
            int x=(int)Math.round(Math.cos(a)*radius),z=(int)Math.round(Math.sin(a)*radius);
            put(cx+x,y+(tilted?(int)Math.round(Math.sin(a)*2):0),cz+z,i%15==0?light:"AMETHYST_BLOCK");
        }
        put(cx,y,cz,"CRYING_OBSIDIAN"); put(cx,y+1,cz,light);
    }
    private void corridor(Room a,Room b) {
        int ax=a.x()*48,az=a.z()*48,bx=b.x()*48,bz=b.z()*48;
        boolean alongX=ax!=bx;
        for(int t=0;t<=48;t++) {
            int cx=ax+Integer.signum(bx-ax)*t,cz=az+Integer.signum(bz-az)*t;
            for(int side=-3;side<=3;side++) {
                int x=cx+(alongX?0:side),z=cz+(alongX?side:0);
                put(x,0,z,side==0 && t%6==0?light:trim); put(x,-1,z,base);
                for(int y=1;y<=6;y++) put(x,y,z,Math.abs(side)==3?(y<=2?glass:"AIR"):"AIR");
                // A light canopy binds the bridge to the rooms' architecture.
                put(x,7,z,t%8==0?trim:glass);
            }
        }
    }
}
