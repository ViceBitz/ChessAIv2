import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.*;

/**
 * A class that handles the chess game by utilizing other classes and methods in the project
 * 
 * @author Victor Gong
 * @version 10/30/2024
 *
 */
public class Game
{
	/**
	 * Simulates one turn in the game of chess for a specific player
	 * 
	 * @param board   The current active board
	 * @param display The current active display
	 * @param player  The player to execute next turn
	 * 
	 * @return True if next turn successful, false if resign/failed
	 */
	
	public static ArrayList<Move> moveLog = new ArrayList<>();
	public static String gameTitle = "Unnamed";
	
	public static Color NEAR_COLOR = Color.WHITE;
	public static Color START_COLOR = Color.WHITE;
	public static final boolean LOOP_GAME = false;

	public static SmartPlayerNegamax whiteAI, blackAI;

	private static boolean nextTurn(Board board, BoardDisplay display, Player player)
	{
		display.setTitle(player.getName());
		Move next = player.nextMove();
		if (next == null) {
			return false;
		}
		board.executeMove(next);
		display.clearColors();
		display.setColor(next.getSource(), Color.YELLOW);
		display.setColor(next.getDestination(), Color.YELLOW);
		try
		{
			Thread.sleep(0); //Set to 0 for instant moves
		}
		catch (InterruptedException e)
		{
		}
		moveLog.add(next);
		return true;
	}

	/**
	 * Simulates the full game of chess
	 * 
	 * @param board   The current active board
	 * @param display The current active display
	 * @param white   The white player
	 * @param black   The black player
	 */
	public static void play(Board board, BoardDisplay display, Player white, Player black)
	{
		King whiteKing = board.getKing(Color.WHITE);
		King blackKing = board.getKing(Color.BLACK);
		while (true)
		{
			boolean success;
			success = nextTurn(board, display, START_COLOR==Color.WHITE?white:black);
			if (!success) {
				System.out.println("Game Over. Black Wins through White's Resignation.");
				return;
			}
			if (blackKing.inCheckmate())
			{
				System.out.println("Game Over. White Wins.");
				return;
			}
			success = nextTurn(board, display, START_COLOR==Color.WHITE?black:white);
			if (!success) {
				System.out.println("Game Over. White Wins through Black's Resignation.");
				return;
			}
			if (whiteKing.inCheckmate())
			{
				System.out.println("Game Over. Black Wins.");
				return;
			}
		}
	}

	public static void setupCustomBoard(Board board, String[] rep)
	{
		for (int i=0;i<board.getNumRows();i++)
		{
			for (int j=0;j<board.getNumCols();j++)
			{
				if (rep[8*i+j].length() == 0) continue;
				Color color = rep[8*i+j].charAt(0)=='W'?Color.WHITE:Color.BLACK;
				String prefix = color.equals(Color.WHITE) ? "white_" : "black_";
				char pieceType = rep[8*i+j].charAt(1);
				Piece p = null;
				if (pieceType == 'P') p = new Pawn(color, prefix+"pawn.gif");
				else if (pieceType == 'N') p = new Knight(color, prefix+"knight.gif");
				else if (pieceType == 'B') p = new Bishop(color, prefix+"bishop.gif");
				else if (pieceType == 'R') p = new Rook(color, prefix+"rook.gif");
				else if (pieceType == 'Q') p = new Queen(color, prefix+"queen.gif");
				else if (pieceType == 'K') p = new King(color, prefix+"king.gif");
				p.putSelfInGrid(board, new Location(i,j));
			}
		}
	}

	/**
	 * Sets up the chess board with the correct pieces in their positions (Black faces away)
	 * 
	 * @param board The current active board
	 */
	public static void setupBoard(Board board)
	{
		
		int whiteBackRank = NEAR_COLOR.equals(Color.WHITE) ? 7 : 0;
		int whitePawnRow = NEAR_COLOR.equals(Color.WHITE) ? 6 : 1;
		int kingColumn = NEAR_COLOR.equals(Color.WHITE) ? 4 : 3;
		// Kings
		Piece blackKing = new King(Color.BLACK, "black_king.gif");
		blackKing.putSelfInGrid(board, new Location(7-whiteBackRank, kingColumn));

		Piece whiteKing = new King(Color.WHITE, "white_king.gif");
		whiteKing.putSelfInGrid(board, new Location(whiteBackRank, kingColumn));

		// Queens
		Piece blackQueen = new Queen(Color.BLACK, "black_queen.gif");
		blackQueen.putSelfInGrid(board, new Location(7-whiteBackRank, 7-kingColumn));

		Piece whiteQueen = new Queen(Color.WHITE, "white_queen.gif");
		whiteQueen.putSelfInGrid(board, new Location(whiteBackRank, 7-kingColumn));

		// Rooks
		Piece blackRook1 = new Rook(Color.BLACK, "black_rook.gif");
		blackRook1.putSelfInGrid(board, new Location(7-whiteBackRank, 0));

		Piece blackRook2 = new Rook(Color.BLACK, "black_rook.gif");
		blackRook2.putSelfInGrid(board, new Location(7-whiteBackRank, 7));

		Piece whiteRook1 = new Rook(Color.WHITE, "white_rook.gif");
		whiteRook1.putSelfInGrid(board, new Location(whiteBackRank, 0));

		Piece whiteRook2 = new Rook(Color.WHITE, "white_rook.gif");
		whiteRook2.putSelfInGrid(board, new Location(whiteBackRank, 7));

		// Bishops
		Piece blackBishop1 = new Bishop(Color.BLACK, "black_bishop.gif");
		blackBishop1.putSelfInGrid(board, new Location(7-whiteBackRank, 2));

		Piece blackBishop2 = new Bishop(Color.BLACK, "black_bishop.gif");
		blackBishop2.putSelfInGrid(board, new Location(7-whiteBackRank, 5));

		Piece whiteBishop1 = new Bishop(Color.WHITE, "white_bishop.gif");
		whiteBishop1.putSelfInGrid(board, new Location(whiteBackRank, 2));

		Piece whiteBishop2 = new Bishop(Color.WHITE, "white_bishop.gif");
		whiteBishop2.putSelfInGrid(board, new Location(whiteBackRank, 5));

		// Knights
		Piece blackKnight1 = new Knight(Color.BLACK, "black_knight.gif");
		blackKnight1.putSelfInGrid(board, new Location(7-whiteBackRank, 1));

		Piece blackKnight2 = new Knight(Color.BLACK, "black_knight.gif");
		blackKnight2.putSelfInGrid(board, new Location(7-whiteBackRank, 6));

		Piece whiteKnight1 = new Knight(Color.WHITE, "white_knight.gif");
		whiteKnight1.putSelfInGrid(board, new Location(whiteBackRank, 1));

		Piece whiteKnight2 = new Knight(Color.WHITE, "white_knight.gif");
		whiteKnight2.putSelfInGrid(board, new Location(whiteBackRank, 6));
		
		// Pawns
		for (int i = 0; i < board.getNumCols(); i++)
		{
			Piece pawn = new Pawn(Color.BLACK, "black_pawn.gif");
			pawn.putSelfInGrid(board, new Location(7-whitePawnRow, i));
		}
		
		for (int i = 0; i < board.getNumCols(); i++)
		{
			Piece pawn = new Pawn(Color.WHITE, "white_pawn.gif");
			pawn.putSelfInGrid(board, new Location(whitePawnRow, i));
		}
	}

	public static void main(String args[]) throws IOException, InterruptedException
	{
		/*
		 * PLAY MODE:
		 *    - Game: Set boardside color, bot color, change (other2, other) to (other2, me) if necessary,
		 *      change parameters of SmartPlayerNegamax (other, other2), turn loopGame off
		 * 
		 *    - Evaluation: Set APPROXIMATE_EVALUATION to true (for speed)
		 * 
		 *    - Compression: Set saveToFile to false (or true if want to learn)
		 * 
		 * TRAIN MODE:
		 *    - Game: Set bot color to white, change (other2, me) to (other2, other) if necessary,
		 *      change parameters of SmartPlayerNegamax (other, other2)
		 * 
		 *    - Evaluation: Set APPROXIMATE_EVALUATION to false (for accuracy)
		 * 
		 *    - Compression: Set saveToFile to true, set STATE_DEPTH_CUTOFF and TABLE_SIZE_CUTOFF
		 * 
		 */

		//Game Configurations
		boolean botWhite = false;
		NEAR_COLOR = Color.BLACK;
		
		gameTitle = "Classical";

		//Setup hashing scheme
		Compression.generateZobristKeys();

		//Load shut down hook
		Runtime.getRuntime().addShutdownHook(new CompressionShutdownHook());
		
		CommandLog cmd = new CommandLog();
		cmd.start();

		do {
			//Load game 
			Board board = new Board();
			
			/*
			String[] ret = {
				"","","","","","","","",
				"","","","","","","","",
				"","","","","","","WP","",
				"","WN","WK","","","","","",
				"","","","","BP","","BR","BP",
				"","BK","BP","","","","BP","",
				"","","BP","","","","","",
				"","","","","","","",""
			};
			setupCustomBoard(board, ret);
			board.getKing(Color.WHITE).setMoved(true);
			board.getKing(Color.BLACK).setMoved(true);
			*/

			setupBoard(board);
			
			BoardDisplay display = new BoardDisplay(board);

			//Load opening tree
			Opening.makeOpeningTree(board);

			moveLog.clear();

			/**
			 * Low Depth Player for gauging SmartPlayer skill
			 */
			SmartPlayerNegamax bad = new SmartPlayerNegamax(board, "Jessie Pinkman", Color.WHITE, 3, false, 3500);

			/**
			 * Smart Player constructor
			 * 1. No extra args (defaults to iterativeDeepening, playTime=3500)
			 * 2. playDepth, iterativeDeepening?, playTime
			 */
			blackAI = new SmartPlayerNegamax(board, "Walter Nernst", Color.BLACK, 8, true, 5000);
			whiteAI = new SmartPlayerNegamax(board, "Walter White", Color.WHITE, 8, true, 5000);

			//Print bot details
			whiteAI.printAIDetails();
			
			//Run main play method
			HumanPlayer me = new HumanPlayer(display, board, "Me", botWhite ? Color.BLACK : Color.WHITE);
			if (botWhite) {
				play(board, display, whiteAI, me);
			}
			else {
				play(board, display, me, blackAI);
			}
			
			//Once game over, print move log
			System.out.println("===== MOVE LOG =====");
			for (int i=0;i<moveLog.size();i+=2) {
				System.out.print((i/2+1)+". " + moveLog.get(i).toStandardNotation() + " "
						+ (i+1 < moveLog.size() ? moveLog.get(i+1).toStandardNotation() : "")
						+ " | ");
			}
			//Record all data to their respective files
			CompressionShutdownHook.recordToAllFiles();
			Thread.sleep(10000);
		}
		while (LOOP_GAME);
	}
}
