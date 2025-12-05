import java.awt.*;
import java.util.*;

// Represesents a rectangular game board, containing Piece objects.
public class Board extends BoundedGrid<Piece>
{
	private ArrayList<Piece>[][] pieces;
	private int[][] material;
	private Bitboard bitboard;
	
	// Constructs a new Board with the given dimensions
	public Board()
	{
		super(8, 8);
		pieces = new ArrayList[2][7]; //0 - White, 1 - Black; Pawn through King, index 1-6 (index 0 left empty)
		material = new int[2][2]; //0 - White, 1 - Black; 0 - Midgame, 1 - Endgame; total material of each side
		for (int c=0;c<2;c++) {
			for (int i=1;i<=6;i++) {
				pieces[c][i] = new ArrayList<>();
			}
		}
		bitboard = new Bitboard(this);
		
	}

	/**
	 * Getter function for the associated bitboard
	 * @return The bitboard
	 */
	public Bitboard getBitboard()
	{
		return bitboard;
	}

	// Precondition: move has already been made on the board
	// Postcondition: piece has moved back to its source,
	// and any captured piece is returned to its location
	public void undoMove(Move move)
	{
		Piece piece = move.getPiece();
		Location source = move.getSource();
		Location dest = move.getDestination();
		Piece victim = move.getVictim();
		
		//Promotion
		if (move instanceof PromotionMove) {
			move.getPiece().putSelfInGrid(((PromotionMove) move).getUpgradePiece().getBoard(), source);
			((PromotionMove) move).getUpgradePiece().removeSelfFromGrid();
			
		}
				
		//General movement
		piece.moveTo(source);

		if (victim != null)
			victim.putSelfInGrid(piece.getBoard(), dest);
		
		piece.setMoved(move.getMovedBefore());
		
		//Castle movement
		if (move instanceof CastleMove) {
			Location rookSource = ((CastleMove) move).getRookSource();
			((CastleMove) move).getRook().moveTo(rookSource);
			((CastleMove) move).getRook().setMoved(move.getMovedBefore());
		}
		
		
	}
	
	/**
	 * Adds a piece to the piece array of the board and increments material
	 * @param p The added piece
	 */
	public void addPiece(Piece p)
	{
		int color = p.getColor().equals(Color.WHITE) ? 0 : 1;
		pieces[color][p.getEnum()].add(p);
		material[color][0] += Evaluation.getMaterial(p, false);
		material[color][1] += Evaluation.getMaterial(p, true);
	}
	
	/**
	 * Removes the piece from the piece array of the board and decrements material
	 * @param p The removed piece
	 */
	public void removePiece(Piece p)
	{
		int color = p.getColor().equals(Color.WHITE) ? 0 : 1;
		pieces[color][p.getEnum()].remove(p);
		material[color][0] -= Evaluation.getMaterial(p, false);
		material[color][1] -= Evaluation.getMaterial(p, true);
	}

	/**
	 * Retrieves the list of pieces of a certain pieceEnum and color
	 * 
	 * @param pieceEnum The enum of the piece
	 * @param color The color of the piece
	 * @return An ArrayList of pieces with enum pieceEnum and color 'color'
	 */
	public ArrayList<Piece> getPiecesOfType(int pieceEnum, Color color)
	{
		return pieces[color.equals(Color.WHITE) ? 0 : 1][pieceEnum];
	}
	
	/**
	 * Retrieves the material of a color given if the game is in endgame
	 * @param color The color of material
	 * @param isEndgame If the game is in endgame or not
	 * @return
	 */
	public int getMaterialOfSide(Color color, boolean isEndgame)
	{
		return material[color.equals(Color.WHITE) ? 0 : 1][isEndgame ? 1 : 0];
	}

	/**
	 * Retrieves the king of a certain color
	 * @param color The color of the king
	 * @return The king
	 */
	public King getKing(Color color)
	{
		return (King)getPiecesOfType(King.ENUM, color).get(0);
	}
	
	/**
	 * Checks if a certain location is attacked by any pieces of a color
	 * 
	 * @param loc   The location to check
	 * @param color The piece color
	 * @return True if attacked, false otherwise
	 */
	public boolean isAttacked(Location loc, Color color)
	{
		return bitboard.isAttacked(loc, color);
	}
	
	/**
	 * Checks if a certain location is attacked by a specific piece
	 * 
	 * @param loc   The location to check
	 * @param piece The specific piece
	 * @return True if attacked, false otherwise
	 */
	public boolean isAttackedBy(Location loc, Piece piece)
	{
		return bitboard.isAttackedBy(loc, piece);
	}

	/**
	 * Checks if a piece is pinned
	 * @param p The piece to check
	 * @return True if pinned, false otherwise
	 */
	public boolean piecePinned(Piece p)
	{
		if (p instanceof King || getPiecesOfType(6, p.getColor()).isEmpty()) return false;
		Location oldLoc = p.getLocation();
		Board oldBoard = p.getBoard();
		p.removeSelfFromGrid();
		boolean ret = false;
		if (getKing(p.getColor()).inCheck()) ret = true;
		p.putSelfInGrid(oldBoard, oldLoc);
		return ret;
	}
	
	/**
	 * Checks if a move escapes check by opposite
	 * @param move The escaping move
	 * @return True if evades check, false otherwise
	 */
	public boolean escapesCheck(Move move) {
		Color color = move.getPiece().getColor();
		King king = getKing(color);
		executeMove(move);
		boolean ret = !king.inCheck();
		undoMove(move);
		return ret;
	}
	
	/**
	 * Checks if a specific move delivers check to the enemy king
	 * @param move The move
	 * @return True if delivers check, false otherwise
	 */
	public boolean deliversCheck(Move move)
	{
		Color color = move.getPiece().getColor();
		King king = getKing(oppositeColor(color));
		
		/*
		 * Checking piece is usually the move piece
		 * (unless castle -> rook, or promotion -> upgrade piece)
		 */
		Piece checkingPiece = move.getPiece();
		if (move instanceof CastleMove) {
			checkingPiece = ((CastleMove) move).getRook();
		}
		else if (move instanceof PromotionMove) {
			checkingPiece = ((PromotionMove) move).getUpgradePiece();
		}
		
		executeMove(move);
		boolean ret = king.inCheckBy(checkingPiece);
		undoMove(move);
		return ret;
	}
	
	/**
	 * Returns the opposite color (White to Black, Black to White)
	 * 
	 * @param col The original color
	 * @return The opposite color
	 */
	public static Color oppositeColor(Color col)
	{
		Color opposite = null;
		if (col.equals(Color.WHITE))
		{
			opposite = Color.BLACK;
		}
		else
		{
			opposite = Color.WHITE;
		}
		return opposite;
	}
	
	/**
	 * Helper to check validity and add castle moves to list of possible moves
	 * @param possibleMoves The list of possible moves
	 * @param king The king involved
	 */
	public void addCastleMoves(ArrayList<Move> possibleMoves, King king) {
		if (!king.inCheck() && !king.getMoved()) {
			Location kingLocation = king.getLocation();
			Color color = king.getColor();
			boolean reorientBool = color.equals(Color.WHITE); reorientBool = Game.NEAR_COLOR.equals(color) ? reorientBool : !reorientBool;
			
			Location shortSide = king.sweepTo(reorientBool ? Location.EAST : Location.WEST);
			Location longSide = king.sweepTo(reorientBool ? Location.WEST : Location.EAST);

			Piece shortRook = get(shortSide);
			Piece longRook = get(longSide);
			boolean shortSafe = !isAttacked(new Location(kingLocation.getRow(),kingLocation.getCol()+(reorientBool?1:-1)),oppositeColor(color));
			boolean longSafe = !isAttacked(new Location(kingLocation.getRow(),kingLocation.getCol()-(reorientBool?1:-1)),oppositeColor(color));
			
			if (shortRook != null && shortRook.getColor().equals(color) && shortRook instanceof Rook && !shortRook.getMoved() && shortSafe) {
				CastleMove castle = new CastleMove(king, new Location(kingLocation.getRow(),kingLocation.getCol()+(reorientBool?2:-2)),(Rook)shortRook,reorientBool?1:-1,1);
				if (escapesCheck(castle)) {
					possibleMoves.add(castle);
				}
			}
			if (longRook != null && longRook.getColor().equals(color) && longRook instanceof Rook && !longRook.getMoved() && longSafe) {
				CastleMove castle = new CastleMove(king, new Location(kingLocation.getRow(),kingLocation.getCol()-(reorientBool?2:-2)),(Rook)longRook,reorientBool?-1:1,2);
				if (escapesCheck(castle)) {
					possibleMoves.add(castle);
				}
			}
		}
	}
	
	/**
	 * Checks the castling rights of a color (doesn't check if CAN castle, just if it's still possible)
	 * (neither king nor rook moved + rooks not captured + correct spots)
	 * 
	 * @param color The color to check
	 * @return 0 if no castling rights, 1 if short castle, 2 if long castle, 3 if both
	 */
	public int getCastlingRights(Color color)
	{
		King king = getKing(color);
		if (king.getMoved() || !king.inStartingSquare()) {
			return 0;
		}
		boolean reorientBool = color.equals(Color.WHITE); reorientBool = Game.NEAR_COLOR.equals(color) ? reorientBool : !reorientBool;
		Piece shortRook = get(new Location(king.getLocation().getRow(),reorientBool ? 7 : 0));
		Piece longRook = get(new Location(king.getLocation().getRow(),reorientBool ? 0 : 7)); 
		boolean canCastleShort = shortRook != null && shortRook.getColor().equals(color) && shortRook instanceof Rook && !shortRook.getMoved();
		boolean canCastleLong = longRook != null && longRook.getColor().equals(color) && longRook instanceof Rook && !longRook.getMoved();
		if (canCastleShort && canCastleLong) {
			return 3;
		}
		else if (canCastleShort) {
			return 1;
		}
		else if (canCastleLong) {
			return 2;
		}
		return 0;
	}
	
	/**
	 * Returns an ArrayList of all valid moves for pieces of a certain color
	 * 
	 * @param color The piece color to detect
	 * @return All possible moves
	 */
	public ArrayList<Move> allMoves(Color color)
	{
		ArrayList<Move> possibleMoves = new ArrayList<>();

		//Regular moves
		bitboard.generateAllLegalMoves(possibleMoves, color, false, false);
		
		//Castling
		addCastleMoves(possibleMoves, getKing(color));
		return possibleMoves;
	}

	/**
	 * Returns an ArrayList of all valid captures for pieces of a certain color
	 * 
	 * @param color The piece color to detect
	 * @return All possible moves
	 */
	public ArrayList<Move> allCaptures(Color color)
	{
		ArrayList<Move> possibleMoves = new ArrayList<>();

		//Regular moves
		bitboard.generateAllLegalMoves(possibleMoves, color, true, false);

		return possibleMoves;
	}

	/**
	 * Returns an ArrayList of all valid checks for pieces of a certain color
	 * 
	 * @param color The piece color to detect
	 * @return All possible moves
	 */
	public ArrayList<Move> allChecks(Color color)
	{
		ArrayList<Move> possibleMoves = new ArrayList<>();

		//Regular moves
		bitboard.generateAllLegalMoves(possibleMoves, color, false, true);

		return possibleMoves;
	}

	/**
	 * Executes a move, reflecting it to the board
	 * 
	 * @param move The move to execute
	 */
	public void executeMove(Move move)
	{
		//General Movement
		move.getPiece().moveTo(move.getDestination());
		move.getPiece().setMoved(true);
		//Castle Movement
		if (move instanceof CastleMove) {
			((CastleMove) move).getRook().moveTo(((CastleMove) move).getRookDestination());
			((CastleMove) move).getRook().setMoved(true);
		}
		//Promotion
		if (move instanceof PromotionMove) {
			
			((PromotionMove) move).getUpgradePiece().putSelfInGrid(move.getPiece().getBoard(), move.getDestination());
			((PromotionMove) move).getUpgradePiece().setMoved(true);
		}
		
	}

}