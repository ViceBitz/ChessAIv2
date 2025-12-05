public class CompressedBoard {
    private long[] pieceBB;
    private long[] colorBB;
    private int colorToMove;

    private long compressedState;

    public CompressedBoard(Board board, int colorToMove)
    {
        this.colorToMove = colorToMove;
        this.pieceBB = new long[7];
        this.colorBB = new long[2];

        //Clone bitboard
        Bitboard bb = board.getBitboard();
        for (int pieceEnum=1;pieceEnum<=6;pieceEnum++) {
            pieceBB[pieceEnum] = bb.getPieceBB(pieceEnum);
        }
        colorBB[0] = bb.colorBB[0]; colorBB[1] = bb.colorBB[1];

        compressedState = Compression.compressState(board, colorToMove);
    }

    public CompressedBoard(Board board, long previousCompressedState, Move previousMove, int colorToMove)
    {
        this.colorToMove = colorToMove;
        this.pieceBB = new long[7];
        this.colorBB = new long[2];

        //Clone bitboard
        Bitboard bb = board.getBitboard();
        for (int pieceEnum=1;pieceEnum<=6;pieceEnum++) {
            pieceBB[pieceEnum] = bb.getPieceBB(pieceEnum);
        }
        colorBB[0] = bb.colorBB[0]; colorBB[1] = bb.colorBB[1];

        compressedState = Compression.updateZobristHash(board, colorToMove, previousCompressedState, previousMove);
    }

    public boolean equals(Object other)
	{
		if (other == this) return true;
        if (!(other instanceof CompressedBoard)) return false;

        CompressedBoard otherBoard = (CompressedBoard) other;

        long res = 0L;
        for (int pieceEnum=1;pieceEnum<=6;pieceEnum++) {
            res |= otherBoard.pieceBB[pieceEnum]^pieceBB[pieceEnum];
        }

        return colorToMove == otherBoard.colorToMove && res==0;
	}

    public int hashCode()
    {
        return Long.hashCode(compressedState);
    }
}
