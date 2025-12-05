import java.util.*;
public class OpeningNode
{
    private Move moveEdge;
    private HashMap<CompressedBoard, OpeningNode> children;
    private Vector<CompressedBoard> keys;
    private Random RNG;

    public OpeningNode(Move moveEdge)
    {
        this.moveEdge = moveEdge;
        this.children = new HashMap<>();
        this.keys = new Vector<>();
        this.RNG = new Random();
    }

    public Move getMove()
    {
        return moveEdge;
    }

    public void addChild(OpeningNode child, CompressedBoard compressedState)
    {
        children.put(compressedState, child);
        keys.add(compressedState);
    }

    public OpeningNode getChild(CompressedBoard compressedState)
    {
        return children.get(compressedState);
    }

    public OpeningNode getRandomChild()
    {
        if (keys.isEmpty()) return null;
        return children.get(keys.get(RNG.nextInt(keys.size())));
    }
    public boolean childrenContains(CompressedBoard compressedState)
    {
        return children.containsKey(compressedState);
    }

    public String toString()
    {
        String ret = "PLAYED: " + (moveEdge == null ? "NULL" : moveEdge.toStandardNotation()) + "\nOPTIONS: ";
        for (CompressedBoard k: keys)
        {
            ret += children.get(k).moveEdge.toStandardNotation() + " ";
        }
        ret += "\n";
        return ret;
    }
}