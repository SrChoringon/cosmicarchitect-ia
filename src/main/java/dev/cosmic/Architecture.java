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
            if (!Set.of("observatory", "library", "sanctum", "arena", "gateway", "cathedral").contains(r.kind())) throw new IllegalArgumentException("Tipo de sala desconocido: " + r.kind());
            if (Math.abs((long)r.x()) > 3 || Math.abs((long)r.z()) > 3 || !cells.add(r.x()+":"+r.z())) throw new IllegalArgumentException("Celdas repetidas o fuera de -3..3.");
            if (r.kind().equals("cathedral")) {
                if (p.rooms().size()!=1 || !p.links().isEmpty()) throw new IllegalArgumentException("Una catedral ocupa todo el plano; usa una sola sala y links: [].");
                if(r.radius()<32 || r.radius()>48 || r.height()<36 || r.height()>52) throw new IllegalArgumentException("Catedral: radio 32..48, altura 36..52.");
            } else if (r.radius() < 9 || r.radius() > 16 || r.height() < 10 || r.height() > 20) throw new IllegalArgumentException("Radio: 9..16; altura: 10..20.");
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
        Pos spawn=spawn(p);
        for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) for (int y=1;y<=4;y++) put(spawn.x()+x,y,spawn.z()+z,"AIR");
        return Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
    }
    public static Pos spawn(Plan p) {
        Room first=p.rooms().getFirst();
        return new Pos(first.x()*48,FLOOR+1,first.z()*48-(first.kind().equals("cathedral")?first.radius()+12:0));
    }
    private void put(int x,int y,int z,String material) {
        blocks.put(new Pos(x,FLOOR+y,z),material);
        if (blocks.size()>MAX_BLOCKS) throw new IllegalArgumentException("El plano supera 250000 bloques; reduce salas, radios o alturas.");
    }
    private void room(Room r) {
        if(r.kind().equals("cathedral")) { cathedral(r); return; }
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

    /** Monumental opaque hall. Sparse surface geometry leaves its interior empty. */
    private void cathedral(Room r) {
        int cx=r.x()*48,cz=r.z()*48,w=r.radius(),l=w+24,h=r.height();
        String stone="DEEPSLATE_BRICKS",rib="POLISHED_BLACKSTONE_BRICKS",dark="OBSIDIAN";
        String accent=glass.equals("ORANGE_STAINED_GLASS")?"GILDED_BLACKSTONE":"AMETHYST_BLOCK";
        // Full, double-layer opaque envelope: no glass roof or exposed bridges.
        for(int x=-w;x<=w;x++) for(int z=-l;z<=l;z++) {
            int roof=vault(x,w,h);
            int hash=Math.floorMod(Objects.hash(x,z,seed),37);
            String tile=hash==0?"CRACKED_DEEPSLATE_BRICKS":hash<4?"DEEPSLATE_TILES":stone;
            if(Math.abs(x)<=5) tile="POLISHED_BLACKSTONE";
            if(Math.abs(x)==6 || Math.abs(z)==l-9) tile="CHISELED_POLISHED_BLACKSTONE";
            if(Math.abs(x)>w-9 || Math.abs(z)>l-9) tile=(x+z)%2==0?rib:"POLISHED_DEEPSLATE";
            if((Math.abs(x)==w-8 && z%8==0) || (Math.abs(x)==5 && z%12==0)) tile="CALCITE";
            put(cx+x,0,cz+z,tile);put(cx+x,-1,cz+z,stone);
            put(cx+x,roof,cz+z,"DEEPSLATE_TILES");put(cx+x,roof+1,cz+z,stone);
            if(Math.abs(x)>=w-1 || Math.abs(z)>=l-1) for(int y=1;y<roof;y++)
                put(cx+x,y,cz+z,y<5 || y%10==0?rib:stone);
        }
        // Nave piers, deep wall bays, mouldings and transverse pointed vault ribs.
        int column=w-10,spring=h-18;
        for(int z=-l+14;z<=l-12;z+=16) {
            for(int sign:new int[]{-1,1}) {
                int px=cx+sign*column;
                for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
                    if(Math.abs(dx)==2 && Math.abs(dz)==2) continue;
                    for(int y=1;y<=3;y++) put(px+dx,y,cz+z+dz,rib);
                    for(int y=spring-2;y<=spring;y++) put(px+dx,y,cz+z+dz,y==spring?"CHISELED_POLISHED_BLACKSTONE":rib);
                }
                for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) for(int y=4;y<spring-2;y++)
                    put(px+dx,y,cz+z+dz,Math.abs(dx)+Math.abs(dz)==2?"POLISHED_DEEPSLATE":rib);
                // Narrow engaged shafts make the clustered pier readable at eye level.
                for(int y=4;y<spring-2;y++) put(px-sign*2,y,cz+z,"POLISHED_BASALT");
                // A restrained cornice instead of repeated exposed sea-lantern cubes.
                for(int zz=z-7;zz<=z+7 && zz<=l-2;zz++) for(int y:new int[]{5,22}) {
                    put(cx+sign*(w-2),y,cz+zz,"CHISELED_POLISHED_BLACKSTONE");
                    put(cx+sign*(w-3),y+1,cz+zz,"minecraft:polished_blackstone_brick_slab[type=bottom]");
                }
                wallBay(cx,cz+z+7,sign,w,spring,accent);
                for(int y=1;y<=Math.min(h-12,vault(sign*(w-3),w,h)-2);y++)
                    for(int dx=0;dx<3;dx++) for(int dz=-2;dz<=2;dz++)
                        put(cx+sign*(w-2-dx),y,cz+z+dz,y%7==0?"CHISELED_DEEPSLATE":rib);
            }
            for(int x=-column;x<=column;x++) {
                int y=spring+(int)Math.round(15*Math.sqrt(Math.max(0,1-x*x/(double)(column*column))));
                y=Math.min(y,vault(x,w,h)-1);
                for(int dy=0;dy<=1;dy++) for(int dz=-1;dz<=1;dz++)
                    put(cx+x,y-dy,cz+z+dz,Math.abs(x)<=1?"GILDED_BLACKSTONE":rib);
            }
        }
        // Longitudinal galleries/cornices join columns rather than leaving poles floating.
        for(int sign:new int[]{-1,1}) for(int z=-l+4;z<=l-4;z++) {
            for(int x=column-1;x<=column+1;x++) put(cx+sign*x,spring,cz+z,rib);
            put(cx+sign*column,spring+1,cz+z,"minecraft:polished_blackstone_brick_slab[type=bottom]");
        }
        // Decorative side chapels have deliberate silhouettes and different contents.
        for(int z=-l+22,index=0;z<l-14;z+=16,index++) for(int sign:new int[]{-1,1}) {
            int px=cx+sign*(w-6);
            switch(index%3) {
                case 0 -> {
                    for(int dz=-3;dz<=3;dz++) for(int y=1;y<=5;y++)
                        put(px,y,cz+z+dz,y==5?rib:"BOOKSHELF");
                    for(int dz:new int[]{-4,4}) brazier(px-sign*3,cz+z+dz);
                }
                case 1 -> {
                    for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) put(px+dx,1,cz+z+dz,rib);
                    for(int y=2;y<=7;y++) for(int dx=-1;dx<=1;dx++) if(y<5 || dx==0)
                        put(px+dx,y,cz+z,y==7?"CHISELED_DEEPSLATE":"POLISHED_BASALT");
                    put(px,8,cz+z,"minecraft:amethyst_cluster[facing=up]");
                }
                default -> {
                    for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) if(dx*dx+dz*dz<=5) {
                        put(px+dx,1,cz+z+dz,dark);
                        int high=2+Math.floorMod(Objects.hash(dx,dz,z,seed),4);
                        if((dx+dz)%2==0) for(int y=2;y<=high;y++) put(px+dx,y,cz+z+dz,accent);
                    }
                    brazier(px-sign*4,cz+z);
                }
            }
        }
        // Entrance facade: massive double door relief and layered pointed archivolts.
        endFacade(cx,cz-l+2,w,h,false);
        endFacade(cx,cz+l-2,w,h,true);
        // An astrolabe mosaic breaks up the long nave without blocking traversal.
        for(int x=-14;x<=14;x++) for(int z=-14;z<=14;z++) {
            double dist=Math.hypot(x,z);
            if(dist>14) continue;
            double angle=Math.atan2(z,x);
            boolean spoke=Math.abs(Math.sin(angle*4))<.13;
            String m="POLISHED_BLACKSTONE";
            if(dist>13 || (dist>10 && dist<11) || (dist>5 && dist<6)) m="CHISELED_POLISHED_BLACKSTONE";
            if((dist>12 && dist<13) || (spoke && dist>5 && dist<12)) m="CALCITE";
            if(dist<4 || (dist>6 && dist<7)) m=accent;
            if(dist>10 && dist<11 && (x+z)%3==0) m="GILDED_BLACKSTONE";
            put(cx+x,0,cz-18+z,m);
        }
        // Inlaid geometric panels give the aisles a considered material rhythm.
        for(int sign:new int[]{-1,1}) for(int z=-l+22;z<l-14;z+=16)
            for(int x=-4;x<=4;x++) for(int dz=-4;dz<=4;dz++) {
                int edge=Math.max(Math.abs(x),Math.abs(dz));
                put(cx+sign*(column-10)+x,0,cz+z+dz,edge==4?rib:edge==3?"CHISELED_POLISHED_BLACKSTONE":Math.abs(x)==Math.abs(dz)?"POLISHED_DEEPSLATE":"DEEPSLATE_TILES");
            }
        // Central medallion and eight-point inlaid star. No random floor lamps.
        int altarZ=cz+24;
        for(int x=-15;x<=15;x++) for(int z=-15;z<=15;z++) {
            double dist=Math.hypot(x,z);
            if(dist>15) continue;
            String material=dist>13.5?rib:dist>12.5?"CALCITE":dark;
            boolean ray=(Math.abs(x)<=1 || Math.abs(z)<=1 || Math.abs(Math.abs(x)-Math.abs(z))<=1);
            if(dist<12 && ray) material=dist<8?accent:"CALCITE";
            put(cx+x,0,altarZ+z,material);
        }
        for(int tier=1;tier<=3;tier++) {
            int radius=10-(tier-1)*3;
            for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) if(x*x+z*z<=radius*radius)
                put(cx+x,tier,altarZ+z,Math.hypot(x,z)>radius-1?"CHISELED_POLISHED_BLACKSTONE":rib);
        }
        // Broad, oriented stair runs provide actual access onto the dais.
        for(int side=-2;side<=2;side++) for(int t=1;t<=3;t++)
            put(cx+side,t,altarZ-11+(t-1)*3,"minecraft:polished_blackstone_brick_stairs[facing=south,half=bottom,shape=straight]");
        for(int sign:new int[]{-1,1}) {brazier(cx+sign*12,altarZ-8);brazier(cx+sign*12,altarZ+8);}
        // The cosmic focus is a sculpted eclipsed sphere with three interlaced armillary bands.
        int orbY=Math.min(21,h-16);
        for(int x=-4;x<=4;x++) for(int y=-4;y<=4;y++) for(int z=-4;z<=4;z++) {
            double d=Math.sqrt(x*x+y*y+z*z);
            if(d<=4.3 && d>=3.1) put(cx+x,orbY+y,altarZ+z,x==-3 && z<1?"CRYING_OBSIDIAN":"BLACK_GLAZED_TERRACOTTA");
        }
        for(int i=0;i<720;i++) {
            double a=i*Math.PI/360;
            int ax=(int)Math.round(8*Math.cos(a)),az=(int)Math.round(8*Math.sin(a));
            put(cx+ax,orbY+(int)Math.round(2*Math.sin(a)),altarZ+az,"GILDED_BLACKSTONE");
            put(cx+(int)Math.round(6*Math.cos(a)),orbY+(int)Math.round(7*Math.sin(a)),altarZ+(int)Math.round(3*Math.cos(a)),accent);
            put(cx+(int)Math.round(3*Math.cos(a)),orbY+(int)Math.round(7*Math.sin(a)),altarZ+(int)Math.round(6*Math.cos(a)),"CHISELED_POLISHED_BLACKSTONE");
        }
        // Suspended fixtures: compact warm light, chains and a designed silhouette.
        for(int z:new int[]{-32,-4}) chandelier(cx,cz+z,h-14,h);
        // Perimeter candles are small accents, not repeating full light blocks.
        for(int sign:new int[]{-1,1}) for(int z=-l+18;z<l-10;z+=16)
            put(cx+sign*(column-4),1,cz+z,"minecraft:purple_candle[candles=3,lit=true]");
        // Concealed floor lighting keeps the enormous opaque room playable in vanilla.
        // Carpet transmits block light while covering the full lamp texture.
        for(int sign:new int[]{-1,1}) for(int x:new int[]{8,w-14}) for(int z=-l+12;z<=l-12;z+=12) {
            if(Math.hypot(x,z-24)<16) continue;
            put(cx+sign*x,0,cz+z,"SEA_LANTERN");
            put(cx+sign*x,1,cz+z,"BLACK_CARPET");
        }
    }
    private static int vault(int x,int width,int height) {
        return height-12+(int)Math.round(12*Math.sqrt(Math.max(0,1-x*x/(double)(width*width))));
    }
    private void wallBay(int cx,int z,int sign,int w,int spring,String accent) {
        // Recessed, sealed star niche. Its back is opaque even behind the accent glass.
        for(int dz=-5;dz<=5;dz++) for(int y=7;y<=Math.min(spring-3,24);y++) {
            int top=24-Math.abs(dz);
            if(y>top) continue;
            String m=(Math.abs(dz)==5 || y==7 || y==top)?"CHISELED_POLISHED_BLACKSTONE":"OBSIDIAN";
            put(cx+sign*(w-2),y,z+dz,m);
            if(Math.abs(dz)<=1 && y>=11 && y<=17)
                put(cx+sign*(w-3),y,z+dz,y==14?"CRYING_OBSIDIAN":accent);
        }
        // An actual light source set into the wall behind a small translucent cover.
        put(cx+sign*(w-3),10,z,"SHROOMLIGHT");
        put(cx+sign*(w-4),10,z,glass);
    }
    private void brazier(int x,int z) {
        put(x,1,z,"CHISELED_POLISHED_BLACKSTONE");put(x,2,z,"POLISHED_BLACKSTONE_WALL");
        put(x,3,z,"minecraft:soul_lantern[hanging=false]");
    }
    private void chandelier(int x,int z,int y,int roof) {
        for(int cy=y+2;cy<roof;cy++) put(x,cy,z,"minecraft:iron_chain[axis=y]");
        for(int dx=-4;dx<=4;dx++) for(int dz=-4;dz<=4;dz++) {
            double dist=Math.hypot(dx,dz);
            if(dist>=3.5 && dist<=4.5) {
                put(x+dx,y,z+dz,"GILDED_BLACKSTONE");
                if((dx==0 || dz==0 || Math.abs(dx)==Math.abs(dz))) put(x+dx,y-1,z+dz,"minecraft:soul_lantern[hanging=true]");
            }
        }
        for(int sign:new int[]{-1,1}) for(int t=0;t<=4;t++) {
            put(x+sign*t,y+1,z,"POLISHED_BLACKSTONE_BRICK_WALL");
            put(x,y+1,z+sign*t,"POLISHED_BLACKSTONE_BRICK_WALL");
        }
        put(x,y-1,z,"AMETHYST_BLOCK");put(x,y-2,z,"minecraft:amethyst_cluster[facing=down]");
    }
    private void endFacade(int cx,int z,int width,int height,boolean apse) {
        for(int x=-12;x<=12;x++) {
            int top=25-(int)Math.round(Math.abs(x)*.8);
            for(int y=1;y<=top;y++) {
                boolean frame=Math.abs(x)>=10 || y>=top-1;
                put(cx+x,y,z,frame?"POLISHED_BLACKSTONE_BRICKS":apse?"CRYING_OBSIDIAN":"DARK_OAK_PLANKS");
                if(!frame && (Math.abs(x)==1 || y==6 || y==14)) put(cx+x,y,z+(apse?-1:1),"CHISELED_POLISHED_BLACKSTONE");
            }
        }
        // Circular tracery above the sealed door / apse.
        int cy=Math.min(height-8,31);
        for(int x=-8;x<=8;x++) for(int y=-8;y<=8;y++) {
            double dist=Math.hypot(x,y);
            if(dist<=8.3) put(cx+x,cy+y,z,dist>=7?"GILDED_BLACKSTONE":Math.abs(x)<=1 || Math.abs(y)<=1?"CALCITE":"OBSIDIAN");
        }
        for(int sign:new int[]{-1,1}) for(int x:new int[]{16,width-6}) brazier(cx+sign*x,z+(apse?-4:4));
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
