
/**
 * A class to handle compressing chess states into Strings and managing the data files, allowing for
 * access and retrieval
 * 
 * @author Victor Gong
 * @version 4/11/2023
 */
import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.TreeMap;

public final class Compression
{
	private static HashMap<CompressedBoard, CompressionInfo> allStates = new HashMap<>();
	private static final int TABLE_SIZE_CUTOFF = 8000000; // Maximum states that table/file can hold
	private static final int STATE_DEPTH_CUTOFF = 2; //Only tracks states with depth >= than this (for quie, >= than -this)
	private static CompressionWriter compressionWriter = new CompressionWriter();
	private static final long[] ZOBRIST_KEYS = new long[773];
	private static boolean tableLocked = false; //boolean that locks the state table on true
	
	private static final boolean SAVETOFILE = true;
	
	public static void generateZobristKeys()
	{
		int index = 0;
		//64 squares from the LSB to MSB, 12 pieces with first 6 as White and next 6 as Black (64*12)
		for (int i=0;i<64;i++) {
			for (int j=0;j<12;j++) {
				ZOBRIST_KEYS[index++] = Bitboard.generateRandomMagic();
			}
		}
		//Another 4 for Castling Rights (Short White, Long White, Short Black, Long Black)
		for (int i=0;i<4;i++) {
			ZOBRIST_KEYS[index++] = Bitboard.generateRandomMagic();
		}
		//Last one for side to move = White
		ZOBRIST_KEYS[index] = Bitboard.generateRandomMagic();
	}

	/**
	 * Retrieves the piece hash (which is a long) given its Location and type
	 * @param pieceEnum The piece enum
	 * @param loc The piece location
	 * @param color The piece color
	 * @return The piece hash
	 */
	public static long getPieceHash(int pieceEnum, Location loc, Color color)
	{
		// Enums for pieces (White, Black)
		int[][] enumTable = {
				{0,1,2,3,4,5},
				{6,7,8,9,10,11}
		};

		//Find the piece hash
		int i = loc.getRow(); int j = loc.getCol();
		long pieceHash = 0L;
		int colorEnum = color.equals(Color.WHITE) ? 0 : 1;
		int squareEnum = Bitboard.toBBIndex(i,j);
		if (Game.NEAR_COLOR.equals(Color.BLACK)) squareEnum = 63 - squareEnum; //Always compress White near-side
		pieceHash = ZOBRIST_KEYS[squareEnum*12+enumTable[colorEnum][pieceEnum-1]];

		return pieceHash;
	}

	/**
	 * Get hash of castle status
	 * @param castleStatus The type of castle (1 - short, 2 - long)
	 * @param colorToMove The color to
	 * @return
	 */
	public static long getCastleHash(int castleStatus, int colorToMove)
	{
		long shortHash = ZOBRIST_KEYS[64*12 + (colorToMove==1?0:2)];
		long longHash = ZOBRIST_KEYS[64*12 + (colorToMove==1?1:3)];
		
		if (castleStatus == 1) return shortHash;
		else if (castleStatus == 2) return longHash;
		else return shortHash ^ longHash;
	}
	
	/**
	 * Compresses the current board state into a 64-bit number with Zobrist Hashing
	 * 
	 * @param board The current board
	 * @param color The color to play
	 * @return The compressed string
	 */
	public static long compressState(Board board, int colorToMove)
	{
		long zobristHash = 0L;
		
		//Piece Hashing
		for (int i = 0; i < board.getNumRows(); i++) {
			for (int j = 0; j < board.getNumCols(); j++) {
				Piece p = board.get(i, j);
				if (p != null) zobristHash ^= getPieceHash(p.getEnum(), p.getLocation(), p.getColor());
			}
		}

		/**
		 * Add tag for castling rights
		 */
		int castlingRightsWhite = board.getCastlingRights(Color.WHITE);
		int castlingRightsBlack = board.getCastlingRights(Color.BLACK);

		zobristHash ^= getCastleHash(castlingRightsWhite, 1);
		zobristHash ^= getCastleHash(castlingRightsBlack, -1);

		/*
		 * Add tag for color to play (white)
		 */
		if (colorToMove == 1) zobristHash ^= ZOBRIST_KEYS[64*12+4];

		return zobristHash;
	}

	public static long updateZobristHash(Board board, int colorToMove, long previousHash, Move m)
	{
		if (m == null) return previousHash; //Empty move

		long zobristHash = previousHash;
		Piece srcPiece = m.getPiece(); Location from = m.getSource(); Location to = m.getDestination();
		int castleType = 0;

		//Flip the color
		zobristHash ^= ZOBRIST_KEYS[64*12+4];

		//Remove the source piece from the state
		zobristHash ^= getPieceHash(srcPiece.getEnum(), from, srcPiece.getColor());

		//PromotionMove, must put in a queen
		if (m instanceof PromotionMove) {
			PromotionMove promotion = (PromotionMove) m;
			zobristHash ^= getPieceHash(promotion.getUpgradePiece().getEnum(), to, promotion.getUpgradePiece().getColor());
			return zobristHash; //Immediately return, because don't add original pawn back in
		}
		//Castle, other than the king moving to a different spot, the rook also moves
		else if (m instanceof CastleMove) {
			CastleMove castle = (CastleMove) m; Rook rook = castle.getRook(); castleType = castle.getType();
			zobristHash ^= getPieceHash(rook.getEnum(), castle.getRookSource(), rook.getColor()); //Remove the rook in old location
			zobristHash ^= getPieceHash(rook.getEnum(), castle.getRookDestination(), rook.getColor()); //Add rook to new location
		}

		//Add the source piece to the new location
		zobristHash = getPieceHash(srcPiece.getEnum(), to, srcPiece.getColor());

		//Update castle status
		int castleStatus = board.getCastlingRights(colorToMove==1?Color.WHITE:Color.BLACK);
		int previousStatus = castleStatus - castleType;
		zobristHash ^= getCastleHash(previousStatus, colorToMove); //Undo previous status
		zobristHash ^= getCastleHash(castleStatus, colorToMove); //Add new status

		return zobristHash;
	}

	/**
	 * Records the move log into the log file (Appends)
	 * @throws IOException 
	 */
	public static void recordMoveLog() throws IOException
	{
		//If no moves, don't record empty log
		if (Game.moveLog.isEmpty()) {
			return;
		}
		compressionWriter.recordMoveLog(Game.moveLog);
	}
	
	/**
	 * Adds a state with a processed depth to the state table
	 * 
	 * @param key            The current state, compressed as a string
	 * @param nodeType		 The type of node
	 * @param depth			 The depth of the search
	 * @param value          The evaluation value of the state
	 */
	public static void addToTable(CompressedBoard key, int nodeType, int depth, int value, Move PVMove) throws IOException
	{
		if (allStates.size() >= TABLE_SIZE_CUTOFF || tableLocked)
		{
			return;
		}
		CompressionInfo prevInfo = allStates.get(key);
		
		//Don't replace entry in TT if a shallower search (less accurate) than current TT entry (if exists)
		if (prevInfo != null && prevInfo.depth > depth) return;

		//Don't enter entry in TT if replacing a PV node (worse)
		if (prevInfo != null && prevInfo.nodeType == 1) return;

		//Don't enter entry in TT if search depth smaller than cutoff
		if ((depth > 0 && depth < STATE_DEPTH_CUTOFF)) return;
		if ((depth <= 0 && depth < -STATE_DEPTH_CUTOFF)) return;

		//Don't enter entry in TT if checkmate (unreliable)
		if (Math.abs(value) >= 99999999) return;

		allStates.put(key, new CompressionInfo(nodeType, depth, value, PVMove));
	}

	/**
	 * Checks if the state table contains a specific state
	 * 
	 * @param key The current state, compressed as a CompressedBoard
	 * @return True if contains, false otherwise
	 */
	public static boolean tableHasState(CompressedBoard key)
	{
		return allStates.containsKey(key);
	}

	/**
	 * Retrieves a CompressionInfo object from the state table given the appropriate parameters
	 * 
	 * @precondition Key exists in the state table
	 * 
	 * @param key The current state, compressed as a CompressedBoard
	 * @return The evaluation value
	 */
	public static CompressionInfo retrieveFromTable(CompressedBoard key)
	{
		return allStates.get(key);
	}
	
	public static void lockTable()
	{
		tableLocked = true;
	}
	
	public static boolean writerIsRunning()
	{
		return compressionWriter.isWriting() && compressionWriter.isAlive();
	}
	
	public static void printDataDetails()
	{
		DecimalFormat df = new DecimalFormat("###,###,###,###.#");
		System.out.println("----- Compression Settings -----");
		System.out.println("COMPRESSION BASE: 36");
		System.out.println("CURRENT MEMORY USAGE: " + df.format(Runtime.getRuntime().totalMemory()/1024.0/1024.0) + " MB");
		System.out.println("MAX HEAP MEMORY: " + df.format(Runtime.getRuntime().maxMemory()/1024.0/1024.0) + " MB");
		System.out.println("TOTAL STATES IN TABLE: " + df.format(allStates.size()));
		System.out.println("% TABLE SPACE USED: " + df.format(allStates.size()/(double)TABLE_SIZE_CUTOFF*100) + "%");
	}
}
