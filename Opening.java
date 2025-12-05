import java.util.*;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
public final class Opening {
    private static OpeningNode openingTree;
    private static final String OPENING_CSV_FILE = "reducedopenings.tsv";

    public static Move convertNotationToMove(Board board, int color, String notation)
    {
        //Castling
        if (notation.equals("O-O") || notation.equals("O-O-O"))
        {
            ArrayList<Move> castleMoves = new ArrayList<>();
            board.addCastleMoves(castleMoves, board.getKing(color == 1 ? Color.WHITE : Color.BLACK));
            for (Move m: castleMoves)
            {
                if (((CastleMove)m).getType() == 1 && notation.equals("O-O")) return m;
                if (((CastleMove)m).getType() == 2 && notation.equals("O-O-O")) return m;
            }
            return null;
        }
        
        //Normal move/capture
        String[] abbrev = {null,"","N","B","R","Q","K"};
        String pieceAbbrev = notation.substring(0,1);
        int pieceEnum = 1;
        for (int i=1;i<=6;i++){
            if (abbrev[i].equals(pieceAbbrev)){
                pieceEnum = i; break;
            }
        }
        ArrayList<Piece> possiblePieces = board.getPiecesOfType(pieceEnum, color == 1 ? Color.WHITE : Color.BLACK);
        String locAbbrev = (notation.substring(notation.length()-1).equals("#") ||
                            notation.substring(notation.length()-1).equals("+")) ?
                            notation.substring(notation.length()-3, notation.length()-1) :
                            notation.substring(notation.length()-2);
        
        ArrayList<Move> possibleMoves = new ArrayList<>();
        for (Piece p: possiblePieces)
        {
            for (Location loc: p.destinations())
            {
                if (loc.toStandardNotation().equals(locAbbrev)) possibleMoves.add(new Move(p, loc));
            }
        }
        
        if (possibleMoves.size() == 0) return null;
        if (possibleMoves.size() == 1) return possibleMoves.get(0);
        String matchRank = pieceEnum == 1 ? notation.substring(0, 1) : notation.substring(1, 2);
        for (Move m: possibleMoves)
        {
            if (matchRank.matches("-?\\d+(\\.\\d+)?")){
                if (m.getPiece().getLocation().toStandardNotation().substring(1,2).equals(matchRank)) return m;
            }
            else{
                if (m.getPiece().getLocation().toStandardNotation().substring(0,1).equals(matchRank)) return m;
            }
            
        }
        return null;
    }
    private static void addLine(OpeningNode node, Board board, int color, Vector<String> moves, int index, String rawLine)
    {
        if (index >= moves.size()) return;

        Move currentMove = convertNotationToMove(board, color, moves.get(index));
        if (currentMove == null) {System.out.println("Could not do: " + moves.get(index) + " | " + rawLine + " | " + color); return;}
        //System.out.println(index + " " + moves.get(index) + " " + currentMove + " " + color);
        board.executeMove(currentMove);

        CompressedBoard compressedState = new CompressedBoard(board, -color);
        if (node.childrenContains(compressedState))
        {
            addLine(node.getChild(compressedState), board, -color, moves, index+1, rawLine);
        }
        else
        {
            OpeningNode child = new OpeningNode(currentMove);
            node.addChild(child, compressedState);
            addLine(child, board, -color, moves, index+1, rawLine);
        }

        board.undoMove(currentMove);
    }
    public static void makeOpeningTree(Board board) throws IOException
    {

        //Add initial dummy root --null--> starting board node
        openingTree = new OpeningNode(null);
        OpeningNode root = new OpeningNode(null);
        openingTree.addChild(root, new CompressedBoard(board, 1));

        //Read from the CSV file
        //Read in the state table from data file
		BufferedReader br = new BufferedReader(new FileReader(OPENING_CSV_FILE));
		StringTokenizer s;
		String nextLine;
		while ((nextLine = br.readLine()) != null)
		{
            String moves = nextLine.split("\t")[2];
            s = new StringTokenizer(moves);

            Vector<String> moveList = new Vector<>();
            while (s.hasMoreTokens())
            {
                String abbrev = s.nextToken();
                if (abbrev.substring(abbrev.length()-1).equals(".")) continue;
                moveList.add(abbrev);
            }
            if (nextLine.contains("Gambit")) continue; //Don't include any gambits (risky)
            addLine(root, board, 1, moveList, 0, nextLine);
		}
		br.close();
    }

    //Clarification: Node is the node right before the enemy's last move, so first have to advance the trie, then pick a random move
    public static OpeningNode getNextNode(Board board, Color color, OpeningNode node)
    {
        OpeningNode nxt = node.getChild(new CompressedBoard(board, color.equals(Color.WHITE) ? 1 : -1));
        if (nxt == null) return null;
        return nxt.getRandomChild();
    }

    public static OpeningNode requestTree(Color color)
    {
        if (openingTree == null) return null;
        return color.equals(Color.WHITE) ? openingTree : openingTree.getRandomChild();
    }
}
