import java.awt.Color;

public class PromotionMove extends Move
{
	private Piece upgradePiece;

	// Constructs a new move for moving the given piece to the given destination.
	public PromotionMove(Piece piece, String promo, Location destination)
	{
		super(piece, destination);

		String fileName = "";
		this.upgradePiece = null;

		switch (promo) {
			case "q" :
				fileName = piece.getColor().equals(Color.WHITE) ? "white_queen.gif" : "black_queen.gif";
				this.upgradePiece = new Queen(piece.getColor(), fileName);
				break;
			case "n" :
				fileName = piece.getColor().equals(Color.WHITE) ? "white_knight.gif" : "black_knight.gif";
				this.upgradePiece = new Knight(piece.getColor(), fileName);
				break;
			case "r" :
				fileName = piece.getColor().equals(Color.WHITE) ? "white_rook.gif" : "black_rook.gif";
				this.upgradePiece = new Rook(piece.getColor(), fileName);
				break;
			case "b" :
				fileName = piece.getColor().equals(Color.WHITE) ? "white_bishop.gif" : "black_bishop.gif";
				this.upgradePiece = new Bishop(piece.getColor(), fileName);
				break;
		}
	}

	// Returns the piece being moved
	public Piece getUpgradePiece()
	{
		return upgradePiece;
	}

	// Returns a string description of the move
	public String toString()
	{
		return super.toString() + " || " + getPiece() + " promotes to " + upgradePiece;
	}
	
	// Returns the standard notation of the chess move
	public String toStandardNotation()
	{
		return super.toStandardNotation() + "=" + upgradePiece.toStandardNotation();
	}
}
