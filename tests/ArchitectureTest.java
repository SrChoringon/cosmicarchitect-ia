import dev.cosmic.Architecture;
import dev.cosmic.Architecture.*;
import java.util.*;
import java.nio.file.*;

public class ArchitectureTest {
    static int checks=0;
    static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    static void rejected(Plan p) { try { new Architecture().generate(p); throw new AssertionError("Accepted invalid plan"); } catch(IllegalArgumentException expected) { checks++; } }
    static Plan demo(String palette,long seed) {
        return new Plan("Eclipse",palette,seed,List.of(
            new Room("umbral","gateway",0,0,12,14), new Room("archivo","library",1,0,14,14),
            new Room("observatorio","observatory",1,1,15,18),new Room("santuario","sanctum",0,1,13,16),new Room("eclipse","arena",1,2,16,20)),
            List.of(new Link("umbral","archivo"),new Link("archivo","observatorio"),new Link("observatorio","santuario"),new Link("observatorio","eclipse")));
    }
    static boolean walkable(Map<Pos,String> blocks,int x,int z) {
        return !blocks.getOrDefault(new Pos(x,80,z),"AIR").equals("AIR")
            && blocks.getOrDefault(new Pos(x,81,z),"AIR").equals("AIR")
            && blocks.getOrDefault(new Pos(x,82,z),"AIR").equals("AIR");
    }
    static void reachable(Plan p,Map<Pos,String> blocks) {
        Room first=p.rooms().getFirst(); Set<Pos> reached=new HashSet<>();
        ArrayDeque<Pos> queue=new ArrayDeque<>(); queue.add(new Pos(first.x()*48,81,first.z()*48));
        while(!queue.isEmpty()) {
            Pos pos=queue.remove(); if(!reached.add(pos)) continue;
            for(int[] step:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int x=pos.x()+step[0],z=pos.z()+step[1];
                if(walkable(blocks,x,z)) { Pos n=new Pos(x,81,z); if(!reached.contains(n)) queue.add(n); }
            }
        }
        for(Room r:p.rooms()) check(reached.contains(new Pos(r.x()*48,81,r.z()*48)),"Unreachable room "+r.id());
    }
    public static void main(String[] args) throws Exception {
        long started=System.nanoTime(); Plan p=demo("void",412778);
        Map<Pos,String> blocks=new Architecture().generate(p);
        check(blocks.size()<=Architecture.MAX_BLOCKS,"Budget exceeded"); reachable(p,blocks);
        check(blocks.equals(new Architecture().generate(p)),"Non deterministic");
        check(!blocks.equals(new Architecture().generate(demo("void",1))),"Seed has no effect");
        for(String palette:List.of("nebula","eclipse")) { Map<Pos,String> other=new Architecture().generate(demo(palette,412778)); check(!blocks.equals(other),"Identical palette"); reachable(p,other); }
        rejected(new Plan("bad","void",1,List.of(),List.of()));
        rejected(new Plan("bad","void",1,p.rooms(),List.of()));
        rejected(new Plan("bad","wrong",1,p.rooms(),p.links()));
        rejected(new Plan("bad","void",1,List.of(new Room("x","gateway",Integer.MIN_VALUE,0,12,14)),List.of()));
        rejected(new Plan("bad","void",1,List.of(new Room("x","gateway",0,0,99,14)),List.of()));
        rejected(new Plan("bad","void",1,List.of(new Room("x","gateway",0,0,12,14),new Room("x","arena",1,0,12,14)),List.of()));
        rejected(new Plan("bad","void",1,p.rooms(),List.of(new Link("umbral","eclipse"))));
        rejected(new Plan("bad","void",1,p.rooms(),List.of(new Link("umbral","missing"))));
        ArrayList<Link> duplicate=new ArrayList<>(p.links()); duplicate.add(new Link("archivo","umbral"));
        rejected(new Plan("bad","void",1,p.rooms(),duplicate));
        // Every supported archetype, minimum and maximum dimensions, negative cells.
        for(String kind:List.of("gateway","library","observatory","sanctum","arena")) for(int radius:new int[]{9,16}) {
            Plan single=new Plan("single","void",3,List.of(new Room("spawn",kind,-1,-1,radius,20)),List.of());
            Map<Pos,String> map=new Architecture().generate(single); check(walkable(map,-48,-48),"Spawn blocked: "+kind);
        }
        List<Room> large=new ArrayList<>();List<Link> largeLinks=new ArrayList<>();
        int[][] cells={{-2,-2},{-1,-2},{0,-2},{1,-2},{2,-2},{2,-1},{1,-1},{0,-1},{-1,-1},{-2,-1}};
        for(int i=0;i<cells.length;i++) {large.add(new Room("r"+i,"library",cells[i][0],cells[i][1],16,20));if(i>0) largeLinks.add(new Link("r"+(i-1),"r"+i));}
        Plan largest=new Plan("large","void",1,large,largeLinks);
        Map<Pos,String> big=new Architecture().generate(largest); check(big.size()<=Architecture.MAX_BLOCKS,"Large plan exceeded budget");reachable(largest,big);
        // Geometry dump used for a labelled floor-plan preview, not a game screenshot.
        StringBuilder out=new StringBuilder();
        for(var e:blocks.entrySet()) if(e.getKey().y()==80) out.append(e.getKey().x()).append(',').append(e.getKey().z()).append(',').append(e.getValue()).append('\n');
        Files.writeString(Path.of("build/floor.csv"),out);
        System.out.println("PASS: "+checks+" checks; demo "+blocks.size()+" positions, largest "+big.size()+" positions; "+((System.nanoTime()-started)/1_000_000)+" ms total.");
    }
}
