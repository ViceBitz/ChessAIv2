import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;

/**
 * A subclass of Player that chooses a smart valid move each time, experimenting with NegaMax
 * 
 * SEARCH FRAMEWORK
 * - Negamax
 * - Quiescence Search
 * - Move Sorting
 * PRUNING
 * - Alpha-beta
 * - Null-move
 * - Futility
 * - Reverse futility
 * - Late move reductions
 * EVALUATION
 * - Material
 * - Position Table
 * - King Safety
 * - Bishop Pairs
 * - Mobility
 * - Development
 * - Pawn Structure
 * - Tapered Evaluation
 * - Play style multipliers
 * OTHER
 * - Transposition Table (Zobrist Hashing)
 * - Bitboard
 * 
 * @author Victor Gong
 * @version 12/15/2023
 *
 */
public class SmartPlayerNegamax extends Player
{
	
	private int PLAY_DEPTH;
	
	//Constants
	
	private static final int QUIESCENCE_PRUNING_BIG_DELTA = 975;
	private static final int QUIESCENCE_PRUNING_MARGIN_DELTA = 225;
	
	private static final int NEGAMAX_FUTILITY_FRONTIER_MARGIN = 325;
	private static final int NEGAMAX_FUTILITY_PREFRONTIER_MARGIN = 525;
	
	private static final int INF = Integer.MAX_VALUE;

	//Iterative deepening
	private boolean ITERATIVE_DEEPENING;
	private int PLAY_TIME;
	private boolean time_break = false;
	private int baseline_depth;
	
	//General structures
	private OpeningNode currentOpening;
	private Move bestMove = null;
	private Move[] prevMoves;

	//Debug & Performance
	private long inner_nodes = 0;
	private long leafs = 0;
	private long q_nodes = 0;
	private long total_nodes = 0;
	private long nodesProcessedByTT = 0;
	private long timeStart = 0;
	private int moveCount = 1;
	private String detailedLines = "";
	private final boolean SHOW_LINES = true;
	private final boolean SHOW_DETAILED_LINES = true;
	
	//Toggles
	private final boolean USE_TT_NEGAMAX = true;
	private final boolean USE_TT_QUIESCENCE = false;
	private final boolean QUIESCENCE_SEARCHES_CHECKS = true;
	private final boolean USE_FUTILITY_PRUNING = true;
<<<<<<< HEAD
	private final boolean USE_NMP = false;
=======
	private final boolean USE_NMP = true;
>>>>>>> 14a5aa60bbbe38f199cbd8873ceca9a3c873618a

	//Heuristics Data Structures
	private Move[][][][] counterMove = new Move[8][8][8][8]; //Counter-move for ordering
	private Move[][] killerMove = new Move[32][2]; //Killer-move for ordering
	
	public SmartPlayerNegamax(Board board, String name, Color color, int baselineDepth) throws IOException
	{
		super(board, name, color);
		this.prevMoves = new Move[4];
		this.PLAY_DEPTH = baselineDepth;
		this.ITERATIVE_DEEPENING = true;
		this.PLAY_TIME = 3500;
		this.baseline_depth = baselineDepth;
		this.currentOpening = Opening.requestTree(color);
	}
	
	public SmartPlayerNegamax(Board board, String name, Color color, int playDepth, boolean deepening, int playTime) throws IOException
	{
		super(board, name, color);
		this.prevMoves = new Move[4];
		this.PLAY_DEPTH = playDepth;
		this.ITERATIVE_DEEPENING = deepening;
		this.PLAY_TIME = playTime;
		this.baseline_depth = playDepth;
		this.currentOpening = Opening.requestTree(color);
	}

	/**
	 * Triples play time increases play depth by 1 in a precision scenario
	 * @postcondition PLAY_TIME *= 3, PLAY_DEPTH += 1, baseline_depth += 1
	 */
	public void togglePrecisionPressure(boolean status) {
		if (status) {
			this.PLAY_TIME *= 3;
			this.PLAY_DEPTH += 1;
			this.baseline_depth += 1;
		}
		else {
			this.PLAY_TIME /= 3;
			System.out.println(this.PLAY_TIME);
			this.PLAY_DEPTH -= 1;
			this.baseline_depth -= 1;
		}
	}

	/**
	 * 10x play time increases play depth by 2 in an extreme precision scenario
	 * @postcondition PLAY_TIME *= 10, PLAY_DEPTH += 2, baseline_depth += 2
	 */
	public void toggleExtremePrecisionPressure(boolean status) {
		if (status) {
			this.PLAY_TIME *= 10;
			this.PLAY_DEPTH += 2;
			this.baseline_depth += 2;
		}
		else {
			this.PLAY_TIME /= 10;
			this.PLAY_DEPTH -= 2;
			this.baseline_depth -= 2;
		}
	}
	
	/**
	 * Sorts hopeless non-captures with some speculative measures
	 * @param m1 The first move
	 * @param m2 The second move
	 * @return > 0 if m2 first, < 0 if m1 first
	 */
	private int sortNonCaptures(Move m1, Move m2, boolean isEndgame)
	{
		/**
		 * Non-endgame:
		 * - Regular pieces (not Pawn, King, Queen) first
		 * - Move queen
		 * - Push pawns
		 * - Move king
		 * Endgame:
		 * - Move king
		 * - Move regular pieces
		 * - Move queen
		 * - Push pawns
		 */

		if (!isEndgame)
		{
			//Move normal pieces (excludes Pawn, King, Queen)
			boolean m1Normal = !(m1.getPiece() instanceof Pawn || m1.getPiece() instanceof King || m1.getPiece() instanceof Queen);
			boolean m2Normal = !(m2.getPiece() instanceof Pawn || m2.getPiece() instanceof King || m2.getPiece() instanceof Queen);
			if (m1Normal && !m2Normal) return -1;
			if (m2Normal && !m1Normal) return 1;
			if (m1Normal && m2Normal) return Evaluation.getMaterial(m2.getPiece(), isEndgame) - Evaluation.getMaterial(m1.getPiece(), isEndgame);

			//Move queen
			boolean m1Queen = m1.getPiece() instanceof Queen;
			boolean m2Queen = m2.getPiece() instanceof Queen;
			if (m1Queen && !m2Queen) return -1;
			if (m2Queen && !m1Queen) return 1;

			//Push pawns
			boolean m1Pawn = m1.getPiece() instanceof Pawn;
			boolean m2Pawn = m2.getPiece() instanceof Pawn;
			if (m1Pawn && !m2Pawn) return -1;
			if (m2Pawn && !m1Pawn) return 1;
			
			//Move king
			return 0;
		}
		else
		{
			//Move king
			boolean m1King = m1.getPiece() instanceof King;
			boolean m2King = m2.getPiece() instanceof King;
			if (m1King && !m2King) return -1;
			if (m2King && !m1King) return 1;

			//Move normal pieces (excludes Pawn, King, Queen)
			boolean m1Normal = !(m1.getPiece() instanceof Pawn || m1.getPiece() instanceof King || m1.getPiece() instanceof Queen);
			boolean m2Normal = !(m2.getPiece() instanceof Pawn || m2.getPiece() instanceof King || m2.getPiece() instanceof Queen);
			if (m1Normal && !m2Normal) return -1;
			if (m2Normal && !m1Normal) return 1;
			if (m1Normal && m2Normal) return Evaluation.getMaterial(m2.getPiece(), isEndgame) - Evaluation.getMaterial(m1.getPiece(), isEndgame);

			//Move queen
			boolean m1Queen = m1.getPiece() instanceof Queen;
			boolean m2Queen = m2.getPiece() instanceof Queen;
			if (m1Queen && !m2Queen) return -1;
			if (m2Queen && !m1Queen) return 1;

			//Push pawns
			boolean m1Pawn = m1.getPiece() instanceof Pawn;
			boolean m2Pawn = m2.getPiece() instanceof Pawn;
			if (m1Pawn && !m2Pawn) return -1;
			if (m2Pawn && !m1Pawn) return 1;

			return 0;
		}
		
	}

	/**
	 * Sorts the moves in a fashion to maximize cutoffs and in an efficient manner
	 * @param moves The move list
	 */
	private void sortMoves(ArrayList<Move> moves, Move previousMove, int plysLeft, Move PVMove) {
		Board board = getBoard();
		boolean isEndgame = Evaluation.isEndgame(board);

		moves.sort((m1, m2) -> {
			/**
			 * Sort order:
			 * PV Moves
			 * Good captures/promotions
			 * Neutral captures
			 * Counter move heuristic
			 * Neutral normal-piece movements (knight, bishop, rook..etc)
			 * Pawn movements (pushing a pawn)
			 * Bad captures
			 */
			int m1Score = 0; int m2Score = 0;

			//Check for PV moves (from TT)
			if (PVMove != null) {
				if (m1.equals(PVMove)) return -1;
				if (m2.equals(PVMove)) return 1;
			}
			//Check for captures/promotions
			if (m1.getVictim() != null || m1 instanceof PromotionMove) {
				m1Score += 100+Evaluation.SEE(board, m1)*1000;
			}
			if (m2.getVictim() != null || m2 instanceof PromotionMove) {
				m2Score += 100+Evaluation.SEE(board, m2)*1000;
			}
			int capturePromoComp = m2Score - m1Score;

			if (capturePromoComp == 0)
			{
				//Killer move heuristic
				if (isKillerMove(m1, plysLeft)) m1Score += 500;
				if (isKillerMove(m2, plysLeft)) m2Score += 500;
				int killerComp = m2Score - m1Score;

				if (killerComp == 0) {
					//Counter-move heuristic
					if (previousMove != null) {
						int counterMoveBonus = 100;
						Location from = previousMove.getSource();
						Location to = previousMove.getDestination();
						Move counter = counterMove[from.getRow()][from.getCol()][to.getRow()][to.getCol()];
						m1Score += m1.equals(counter) ? counterMoveBonus : 0;
						m2Score += m2.equals(counter) ? counterMoveBonus : 0;
					}
					
					int CMComp = m2Score - m1Score;
					if (CMComp == 0)
					{
						return sortNonCaptures(m1, m2, isEndgame);
					}
					else
					{
						return CMComp;
					}
				}
				else
				{
					return killerComp;
				}
			}
			else {
				return capturePromoComp;
			}
		});
	}
	
	/**
	 * Updates the counter table to the current move that produced a beta-cutoff
	 * @param m The current move
	 * @param prev The previous move
	 */
	private void updateCounterTable(Move m, Move prev)
	{
		//If non-capture move and previous move exists, set counter-move
		if (prev != null && m.getVictim() == null)
		{
			Location from = prev.getSource();
			Location to = prev.getDestination();
			counterMove[from.getRow()][from.getCol()][to.getRow()][to.getCol()] = m;
		}
	}
	
	/**
	 * Updates the killer table by pushing quiet moves that produced beta-cutoff
	 * @param m The current move
	 */
	private void updateKillerTable(Board board, Move m, int plys)
	{
		//Check if quiet move
		if (m.getVictim() != null || (m instanceof PromotionMove) || board.deliversCheck(m)) return;

		//Check if quiescence
		if (plys < 0) plys += 32;

		//Add to killer table
		if (killerMove[plys][0] == null) {
			killerMove[plys][0] = m;
		}
		else if (!killerMove[plys][0].equals(m) && killerMove[plys][1] == null) {
			killerMove[plys][1] = m;
		}
		else if (!killerMove[plys][0].equals(m) && !killerMove[plys][1].equals(m)) {
			killerMove[plys][0] = killerMove[plys][1];
			killerMove[plys][1] = m;
		}
	}

	private boolean isKillerMove(Move m, int plys)
	{
		if (plys < 0) plys += 32;
		return (killerMove[plys][0] != null && killerMove[plys][0].equals(m)) || (killerMove[plys][1] != null && killerMove[plys][1].equals(m));
	}

	/**
	 * Helps retrieve an evaluation score, either from calculation or data file
	 * @param compressedState The compressed version of the board
	 * @return The ABSOLUTE evaluation (not relative)
	 * @throws IOException 
	 */
	private CompressionInfo retrieveEvaluation(CompressedBoard compressedState) throws IOException
	{
		CompressionInfo table_info = null;
		//Check if leaf calculation already done
		if (Compression.tableHasState(compressedState)) {
			table_info = Compression.retrieveFromTable(compressedState);
		}
		return table_info;
	}


	/**
	 * Quiescence search to ensure that there's no traps or capturebacks
	 * @param alpha The max value
	 * @param beta The cutoff value
	 * @param color The current color
	 * @param depth The current depth
	 * @return The evaluational value
	 * @throws IOException
	 */
	private EvaluationInfo quiescence(int alpha, int beta, int color, int depth, int maxDepth, Move previousMove, boolean nullMoveSearch) throws IOException
	{
		Board board = getBoard();
		int plysLeft = maxDepth - depth;
		Color pieceColor = color == 1 ? Color.WHITE : Color.BLACK;
		boolean inCheck = board.getKing(pieceColor).inCheck();
		
		q_nodes++;

		//Stand pat
		EvaluationInfo absoluteEval = Evaluation.evaluate(board);
		int evalScore = absoluteEval.value * color;
		
		boolean ableDeltaPrune = !inCheck && !Evaluation.isEndgame(board);

		//Don't use stand pat as lower bound if in check (special case)
		if (!inCheck) {
					
			/* If rating >= beta, then opponent (parent) already has a move
			 * that favors them more than this path, so break
			 */
			
			if (evalScore >= beta) {
				return new EvaluationInfo(evalScore, absoluteEval.splits);
			}
		
			if (alpha < evalScore) {
				alpha = evalScore;
			}
		}
		
		//Generate moves (will be generated in eval anyway)
		ArrayList<Move> moves;
		if (inCheck) {
			moves = board.allMoves(pieceColor);
		}
		else {
			moves = board.allCaptures(pieceColor);
		}

		//Check for checkmate
		if (moves.size() == 0)
		{
			if (inCheck)
			{
				return new EvaluationInfo(-INF + depth);
			}
		}

		//Check time break for iterative deepening
		if (time_break) {
			return new EvaluationInfo(10000);
		}

		//Delta Pruning
		if (ableDeltaPrune && evalScore + QUIESCENCE_PRUNING_BIG_DELTA < alpha && USE_FUTILITY_PRUNING)
		{
			return new EvaluationInfo(evalScore, absoluteEval.splits);
		}
		
		//Sort the moves
		sortMoves(moves, previousMove, plysLeft, null);
		
		EvaluationInfo value = new EvaluationInfo(evalScore, absoluteEval.splits);
		int alphaRaisedCount = 0;

		//Searches through all captures (and promotions) (or all moves if in check)
		for (Move m: moves) {
			
			int SEEValue = Evaluation.SEE(board, m);

			//Ignore bad captures (SEE < 0)
			if (SEEValue < 0) break;

			//Check additional delta pruning (move-specific)
			if (ableDeltaPrune && evalScore + SEEValue + QUIESCENCE_PRUNING_MARGIN_DELTA < alpha && USE_FUTILITY_PRUNING)
			{
				continue;
			}

			board.executeMove(m);

			if (board.getKing(pieceColor).inCheck()) throw new Error();
			EvaluationInfo absoluteResult = quiescence(-beta, -alpha, -color, depth+1, maxDepth, m, nullMoveSearch);
			int evalResult = -absoluteResult.value;
			if (evalResult > value.value) value = new EvaluationInfo(evalResult, absoluteResult.splits);

			board.undoMove(m);
			
			if (value.value > alpha)
			{
				alpha = value.value; alphaRaisedCount++;
				
				//Beta cutoff
				if (alpha >= beta) {
					updateCounterTable(m, previousMove);
					updateKillerTable(board, m, plysLeft);
					break;
				}
			}
		}

		//Try checking moves if less than 3 captures raised alpha (only plys 0, -1, -2 (first three))
		if (alphaRaisedCount < 3 && Math.abs(plysLeft) <= 2 && QUIESCENCE_SEARCHES_CHECKS) {
			ArrayList<Move> checks = board.allChecks(pieceColor);
			sortMoves(checks, previousMove, plysLeft, null);

			for (Move m: checks) {
				board.executeMove(m);

				EvaluationInfo absoluteResult = quiescence(-beta, -alpha, -color, depth+1, maxDepth, m, nullMoveSearch);
				int evalResult = -absoluteResult.value;
				if (evalResult > value.value) value = new EvaluationInfo(evalResult, absoluteResult.splits);

				board.undoMove(m);
				
				if (value.value > alpha)
				{
					alpha = value.value;
					
					//Beta cutoff
					if (alpha >= beta) {
						updateCounterTable(m, previousMove);
						updateKillerTable(board, m, plysLeft);
						break;
					}
				}
			}
		}
		return value;
		
	}
	/**
	 * The negamax algorithm to find the optimal move, with the following processes (order matters!):
	 * - Move generation and checkmate/statemate check
	 * - Iterative deepening time check
	 * - Leaf node check for quiescence
	 * - Evaluation for pruning
	 * - Reverse Futility Pruning
	 * - TT Probing
	 * - Null Move Pruning
	 * - Move Sort
	 * - Move Loop, Search Recursion
	 * - Late Move Reductions
	 * - TT Storing
	 * 
	 * @return The best score in the subtree
	 * @throws IOException
	 */
	public EvaluationInfo negamax(int depth, int maxDepth, int alpha, int beta, int color, Move previousMove, long previousZobristHash, EvaluationLine currentLine, boolean nullMoveSearch, boolean allowNull) throws IOException
	{
		int plysLeft = maxDepth - depth;
		Color pieceColor = color == 1 ? Color.WHITE : Color.BLACK;
		
		Board board = getBoard();

		//Check if kings eaten (for pseudo-move generation)
		if (board.getPiecesOfType(6, pieceColor).isEmpty()) return new EvaluationInfo(-INF);
		if (board.getPiecesOfType(6, Board.oppositeColor(pieceColor)).isEmpty()) return new EvaluationInfo(INF);

		//Iterative Deepening: If search runs over the play time limit, flag time_break and exit
		//Don't cut time if haven't reached baseline depth
		if (ITERATIVE_DEEPENING && maxDepth > baseline_depth)
		{
			if (time_break || System.currentTimeMillis()-timeStart >= PLAY_TIME) {
				time_break = true;
				return new EvaluationInfo(10000);
			}
		}

		//Get all moves
		ArrayList<Move> moves = board.allMoves(pieceColor);

		//Check for checkmate/draw
		boolean inCheck = board.getKing(pieceColor).inCheck();
		if (moves.size() == 0)
		{
			if (inCheck)
			{
				return new EvaluationInfo(-INF + depth);
			}
			else
			{
				return new EvaluationInfo(0);
			}
		}

		//If leaf node, run evaluation/quiescence search
		if (depth == maxDepth)
		{
			leafs++;
			EvaluationInfo quieRet = quiescence(alpha, beta, color, depth, maxDepth, previousMove, nullMoveSearch);
			return quieRet;
			
		}
		
		// Debugging & Statistics
		inner_nodes++;
		
		/*
		 * Reverse Futility Pruning
		 * 
		 * Conditions:
		 * - Remaining Plys <= 2
		 * - NOT in check
		 * - Beta is not close to mate value
		 */

		
		if (plysLeft <= 2 && !inCheck && Math.abs(beta) < INF-1000 && USE_FUTILITY_PRUNING)
		{
			//Evaluation of current node for pruning purposes
			EvaluationInfo absoluteEval = Evaluation.evaluate(board);
			int evalScore = absoluteEval.value * color;
			if (evalScore - (plysLeft == 2 ? NEGAMAX_FUTILITY_PREFRONTIER_MARGIN : NEGAMAX_FUTILITY_FRONTIER_MARGIN) >= beta)
			{
				currentLine.special = 5;
				return new EvaluationInfo(evalScore, absoluteEval.splits);
			}
		}

		//Probe the state table (transposition table)
		CompressedBoard compressedState = null;
		long zobristHash = 0L;
		Move TTMove = null;

		if (USE_TT_NEGAMAX && !nullMoveSearch) {

			if (previousZobristHash == 0) compressedState = new CompressedBoard(board, color);
			else compressedState = new CompressedBoard(board, previousZobristHash, previousMove, color); //Update previous hash
			
			zobristHash = compressedState.hashCode();

			CompressionInfo table_info = retrieveEvaluation(compressedState);
			if (table_info != null) 
			{
				if (table_info.depth >= plysLeft && depth > 0) { //Do not risk auto-return on root
					//PV Node (Exact)
					if (table_info.nodeType == 1) {
						nodesProcessedByTT++;
						currentLine.special = 1;
						return new EvaluationInfo(table_info.score);
					}
					//Upper Bound (<= alpha) (improves beta)
					else if (table_info.nodeType == 2) {
						if (table_info.score <= alpha) {
							nodesProcessedByTT++;
							currentLine.special = 2;
							return new EvaluationInfo(table_info.score);
						}
					}
					//Lower Bound (>= beta) (improves alpha)
					else if (table_info.nodeType == 3) {
						if (table_info.score >= beta)
						{
							nodesProcessedByTT++;
							currentLine.special = 3;
							return new EvaluationInfo(table_info.score);
						}
					}
				}
				TTMove = table_info.PVMove; //Regardless of depth, is a good starting point/approx. for move sorting
			}
		}

		/*
		 * Null Move Pruning (may slow the program down + very inaccurate at the moment)
		 * 
		 * Conditions:
		 * - NOT Frontier Node (plysLeft == 1)
		 * - NOT Root Node (depth == 0)
		 * - NOT previous move is null
		 * - NOT in check
		 * - Must have pieces other than pawns (count > 4 ~~ 12pts = endgame)
		 * - allowNull is true (don't allow 2 consecutive null moves)
		 */
		
		
		int nonPawnPiecesCount = board.getPiecesOfType(Knight.ENUM, pieceColor).size() + 
								 board.getPiecesOfType(Bishop.ENUM, pieceColor).size() + 
								 board.getPiecesOfType(Rook.ENUM, pieceColor).size() +
								 board.getPiecesOfType(Queen.ENUM, pieceColor).size();
		
		if (plysLeft > 1 && depth > 0 && previousMove != null && !inCheck && nonPawnPiecesCount > 4 && allowNull && USE_NMP)
		{
			int R = plysLeft <= 3 ? 1 : (plysLeft <= 6 ? 3 : 4); //[2-3] -> R=1; [4-6] -> R=3; [7+] -> R=4
			EvaluationInfo absoluteNullScore = negamax(depth+R,maxDepth,-beta,-alpha,-color,null,0,new EvaluationLine(null),true,false);
			int nullScore = -absoluteNullScore.value;
			//Cutoff if still better than beta
			if (nullScore >= beta)
			{
				currentLine.special = 4;
				return new EvaluationInfo(nullScore, absoluteNullScore.splits);
			}
		}
		

		//Sort the moves
		sortMoves(moves, previousMove, plysLeft, TTMove);
		EvaluationInfo value = new EvaluationInfo(-INF); boolean valueIsLMR = false; Move PVMove = null;
		int originalAlpha = alpha;
		int movesSearched = 0;
		for (Move m : moves)
		{
<<<<<<< HEAD
			//Don't consider this move if it draws (for immediate move)
			if (depth == 0) {
				if (m.equals(prevMoves[1]) && prevMoves[1].equals(prevMoves[3]) && prevMoves[0].equals(prevMoves[2])) {
					continue;
				}
			}
			

=======
			//Don't consider this move if it draws
			
>>>>>>> 14a5aa60bbbe38f199cbd8873ceca9a3c873618a
			EvaluationLine childLine = new EvaluationLine(null);
			EvaluationInfo absoluteChild = null;
			int childValue = 0;

			board.executeMove(m);
			
			
			/**
			 * Late Move Reductions
			 * 
			 * Conditions:
			 * - Not a capture or promotion
			 * - Not currently in check
			 * - Not a checking move (checks opponent)
			 * - plysLeft >= 3
			 * - movesSearched >= 4
			 */
			
			boolean LMRsuccess = false;
			
			if (!(m.getVictim() != null || (m instanceof PromotionMove)) && !inCheck && plysLeft >= 3 && movesSearched >= 4)
			{
				boolean checksOpponent = board.getKing(Board.oppositeColor(pieceColor)).inCheck();
				if (!checksOpponent) {
					//Try move at a reduced depth (-1 for movesSearched <= 6 then (plys+1)/3 for the rest)
					int R = movesSearched <= 6 ? 1 : (plysLeft+1)/3;
					absoluteChild = negamax(depth+1+R, maxDepth, -beta, -alpha, -color, m, zobristHash, childLine,nullMoveSearch,true);
					childValue = -absoluteChild.value;
					if (childValue <= alpha)
					{
						LMRsuccess = true;
					}
				}
			}
			

			if (!LMRsuccess) {
				//Regular search
				absoluteChild = negamax(depth+1, maxDepth, -beta, -alpha, -color, m, zobristHash, childLine,nullMoveSearch,true);
				childValue = -absoluteChild.value;
			}
			

			board.undoMove(m);

			//Check if this move's evaluation is better than the current best
			if (childValue > value.value)
			{
				value = new EvaluationInfo(childValue, absoluteChild.splits); valueIsLMR = LMRsuccess;
				
				//Update the current line if found better move
				currentLine.bestMove = m;
				currentLine.next = childLine;
			}
			
			/*
			//Debug Output
			if (depth == 0) {
				System.out.println(m+" | "+value/100.0);
			}
			*/
			
			//Debug All Lines Output
			if (SHOW_DETAILED_LINES && depth == 0)
			{
				double adjustedEval = (childValue * color) / 100.0;
				String evalPrint = (adjustedEval == 0 ? "" : (adjustedEval > 0 ? "+" : "-")) + Math.abs(adjustedEval);
				detailedLines += ("Line (" + evalPrint + "): "); //+ "SEE=" + (m.getVictim() != null ? Evaluation.SEE(board, m) : 0) + " ");
				detailedLines += (m.toStandardNotation()) + " ";
				detailedLines += (childLine) + "\n";
			}
			
			
			//Update alpha, update bestMove, and check beta cutoff (too good, opponent won't play this)
			if (value.value > alpha)
			{
				alpha = value.value;
				PVMove = m;

				//Beta-cutoff
				if (alpha >= beta) {
					updateKillerTable(board, m, plysLeft);
					updateCounterTable(m, previousMove);
					break;
				}
			}
			movesSearched++;
		}
		
		//Set best move for root node
		if (depth == 0) bestMove = PVMove;
		
		//TT Store
		if (USE_TT_NEGAMAX && !nullMoveSearch && !time_break && !valueIsLMR) {
			if (value.value <= originalAlpha) {
				//Fail-low (<= alpha)
				Compression.addToTable(compressedState, 2, plysLeft, value.value, PVMove);
			}
			else if (value.value >= beta) {
				//Fail-high (alpha-beta cutoff, >= beta)
				Compression.addToTable(compressedState, 3, plysLeft, value.value, PVMove);
			}
			else {
				//Exact score: alpha < score < beta
				Compression.addToTable(compressedState, 1, plysLeft, value.value, PVMove);
			}
		}
		return value;
	}
	/**
	 * Procedure to run negamax on a certain depth
	 * @return The debug output of the run
	 */
	private String runNegamax(int depth, int color)
	{
		
		//Reset Debug/Performance variables
		bestMove = null;
		inner_nodes = 0;
		leafs = 0;
		q_nodes = 0;
		total_nodes = 0;
		nodesProcessedByTT = 0;
		timeStart = System.currentTimeMillis();
		detailedLines = "";
		time_break = false;

		//Initialize heuristics/debug
		EvaluationInfo score;
		EvaluationLine PVLine = new EvaluationLine(null);
		counterMove = new Move[8][8][8][8];
		killerMove = new Move[32][2];

		//Run negamax
		try
		{
			score = negamax(0, depth, -INF, INF, color, null, 0, PVLine, false, false);
		}
		catch (IOException e)
		{
			score = new EvaluationInfo(0);
		}

		//Debug printing
		total_nodes = inner_nodes + q_nodes; //No leaf nodes because q_nodes includes leafs
		
		double adjustedEval = (score.value * color) / 100.0;
		String evalPrint = (adjustedEval == 0 ? "" : (adjustedEval > 0 ? "+" : "-")) + Math.abs(adjustedEval);
		
		String output = "";
		
		if (SHOW_LINES) {
			if (SHOW_DETAILED_LINES)
			{
				output += ("\nDetailed Lines -> \n");
				output += (detailedLines+"\n");
				output += ("Best -> \n");
			}
			
			output += ("Main Line (" + evalPrint + "): ");
			output += (PVLine + "\n");
		}
		
		output += (moveCount
				+ " | Eval: " + evalPrint
				+ " || "
				+ "Node Data:"
				+ " | Inner: " + inner_nodes
				+ " | Leaf: " + leafs
				+ " | Quies: " + q_nodes
				+ " | Total: " + total_nodes
				+ " | From TT: " + nodesProcessedByTT
				+ " || "
				+ "\nGeneral:"
				+ " | Time Elapsed: " + (System.currentTimeMillis() - timeStart) / 1000.0 + "s"
				+ " | Depth: " + depth)
				+ "\n"
				+ "\n<<Detailed Evaluation>>";
		if (!score.splits.isEmpty()) output += 
				("\nMaterial: " + (score.splits.get(0))/100.0
				+ "\nPosition: " + (score.splits.get(1))/100.0
				+ "\nBishop Pair: " + (score.splits.get(2))/100.0
				+ "\nKing Safety: " + (score.splits.get(3))/100.0
				+ "\nPawn Structure: " + (score.splits.get(4))/100.0
				+ "\nDevelopment: " + (score.splits.get(5))/100.0
				+ "\nMobility: " + (score.splits.get(6))/100.0);
		
		
		return output;
	}
	
	/**
	 * Gets the next move by selecting a random one
	 * 
	 * @return The next move
	 */
	public Move nextMove()
	{
		Board board = getBoard();
		int numColor = getColor().equals(Color.WHITE) ? 1 : -1;
		
		
		//[Opening]\\
		if (currentOpening != null)
		{
			currentOpening = Opening.getNextNode(board, getColor(), currentOpening);
			System.out.println(currentOpening);
			if (currentOpening != null) return currentOpening.getMove();
		}

		//[Run Search]\\

		String runInfo = null;
		
		//Iterative Deepening approach, cap out at certain time
		runInfo = runNegamax(PLAY_DEPTH, numColor);
		if (ITERATIVE_DEEPENING) {
			int addition = 1;
			Move prevBest = null;
			String prevInfo = null;
			
			time_break = false;
			while (!time_break) {
				prevBest = bestMove;
				prevInfo = runInfo;
				//System.out.println("Encountered Simplicity, running depth " + (PLAY_DEPTH+addition));
				runInfo = runNegamax(PLAY_DEPTH+addition, numColor);
				addition++;
			}
			bestMove = prevBest;
			runInfo = prevInfo;
		}
		
		/*
		//If 2 repetitions, run deeper
		if (prevMoves[0] != null && prevMoves[0].equals(prevMoves[2])
				&& bestMove != null && bestMove.equals(prevMoves[1])) {
			
			boolean oldITERATIVE_DEEPENING = ITERATIVE_DEEPENING;
			ITERATIVE_DEEPENING = false; //Shut off Iterative Deepening for now
			
			int addition = 1;
			while (bestMove != null && bestMove.equals(prevMoves[1]) && addition <= 6) {
				System.out.println("Encountered Repetition, running depth " + (PLAY_DEPTH+addition));
				runInfo = runNegamax(PLAY_DEPTH+addition, numColor, addition >= 2 ? prevMoves[1] : null);
				addition++;
			}
			
			ITERATIVE_DEEPENING = oldITERATIVE_DEEPENING; //Return Iterative Deepening to original state
		}
		*/

		//Print search debug output
		System.out.println(runInfo);

<<<<<<< HEAD
		
=======
		/*
>>>>>>> 14a5aa60bbbe38f199cbd8873ceca9a3c873618a
		//Update previous moves
		for (int i=prevMoves.length-1;i>=1;i--) {
			prevMoves[i] = prevMoves[i-1];
		}
		prevMoves[0] = bestMove;
<<<<<<< HEAD
=======
		*/
>>>>>>> 14a5aa60bbbe38f199cbd8873ceca9a3c873618a
		
		//Update move count
		moveCount++;
		
		//Add to move queue
		return bestMove;
		
	}
	
	/**
	 * Prints information about the settings of this player
	 */
	public void printAIDetails() {
		System.out.println("----- AI Settings -----");
		System.out.println("INITIAL DEPTH: " + (PLAY_DEPTH));
		System.out.println("PLAY TIME CUTOFF: " + PLAY_TIME);
		Compression.printDataDetails();
		
	}

}


