import java.awt.*;
import java.util.*;
import java.math.*;

/**
 * Bitboard implementation with move and check sets
 * 
 * @author Victor Gong
 * @version 12/10/2023
 */
public class Bitboard
{
    //---[ASSOCIATIONS]---\\
    private final Board board;

    //---[STANDARD BITBOARDS]---\\
    //Piece bitboards (pawn, knight, bishop, rook, queen, king)
    public long[] pieceBB;
    //Color bitboard
    public long[] colorBB;


    //---[ATTACK SETS]---\\
    public final long[][][] pawnAttackSet;
    public final long[] knightAttackSet;
    public final long[] kingAttackSet;
    public final long[][] rookAttackSet;
    public final long[][] bishopAttackSet;

    //---[[BLOCKER MASKS]]---\\
    public final long[] rookBlockerMasks;
    public final long[] bishopBlockerMasks;

    //---[BLOCKER MASK BITCOUNT SETS]---\\
    public final int[] rookBitCount;
    public final int[] bishopBitCount;

    //---[MAGIC NUMBERS]---\\
    public final long[] rookMagicNumbers;
    public final long[] bishopMagicNumbers;

    //---[CACHES]---\\
    public long[] totalAttackSet;

    //---[CONSTANTS]---\\

    //Bruijn Index Table for getting the least significant 1 bit
    private static final int[] bruijnIndex64 =
    {
        0, 47,  1, 56, 48, 27,  2, 60,
       57, 49, 41, 37, 28, 16,  3, 61,
       54, 58, 35, 52, 50, 42, 21, 44,
       38, 32, 29, 23, 17, 11,  4, 62,
       46, 55, 26, 59, 40, 36, 15, 53,
       34, 51, 20, 43, 31, 22, 10, 45,
       25, 39, 14, 33, 19, 30,  9, 24,
       13, 18,  8, 12,  7,  6,  5, 63
    };

    public Bitboard(Board board)
    {
        //Associations
        this.board = board;

        //Standard
        pieceBB = new long[7]; //0 is left empty
        colorBB = new long[2]; //0 - White, 1 - Black

        //Attack sets
        pawnAttackSet = new long[64][2][2]; //[0 - Moves, 1 - Attacks][0 - Forward, 1 - Backward]
        knightAttackSet = new long[64];
        kingAttackSet = new long[64];
        rookAttackSet = new long[64][4096]; //2^12 different blocking configurations
        bishopAttackSet = new long[64][4096]; //2^9 different blocking configurations

        //Blocker masks
        rookBlockerMasks = new long[64];
        bishopBlockerMasks = new long[64];

        //Bitcount sets
        rookBitCount = new int[64];
        bishopBitCount = new int[64];

        //Magic numbers
        rookMagicNumbers = new long[64];
        bishopMagicNumbers = new long[64];

        //Caches
        totalAttackSet = new long[2]; //Tracks the current attack set so as to not recalculate; reset on bitboard change (with color)

        generatePawnAttackSet();
        generateKnightAttackSet();
        generateKingAttackSet();
        generateRookAttackSet();
        generateBishopAttackSet();

        System.out.println("Bitboard Initialized!");
        
    }
    
    //[[Index Sanity Checks]]\\
    public static boolean isValid(int index)
    {
        return index >= 0 && index <= 63;
    }

    //[[Index-Grid Conversions]]\\
    public static int toBBIndex(int row, int col)
    {
        //Reversed because index 0 = shift 63, index 64 = shift 0
        return 63 - (row * 8 + col);
    }

    public static Location toLocation(int index)
    {
        index = 63 - index;
        return new Location(index/8, index%8);
    }

    public static int toRow(int index)
    {
        return (63 - index)/8;
    }

    public static int toCol(int index)
    {
        return (63 - index)%8;
    }

    //[[Directional Operations]]\\
    public static int toUpIndex(int index)
    {
        if (!isValid(index) || toRow(index) == 0) return -1; //Out of bounds
        return index+8;
    }

    public static int toDownIndex(int index)
    {
        if (!isValid(index) || toRow(index) == 7) return -1; //Out of bounds
        return index-8;
    }

    public static int toLeftIndex(int index)
    {
        if (!isValid(index) || toCol(index) == 0) return -1; //Out of bounds
        return index+1;
    }

    public static int toRightIndex(int index)
    {
        if (!isValid(index) || toCol(index) == 7) return -1; //Out of bounds
        return index-1;
    }
    //Inclusive on capture piece
    public static int raycast(long bitboard, int index, int dr, int dc)
    {
        if (dc < 0) index = toLeftIndex(index);
        if (dc > 0) index = toRightIndex(index);
        if (dr < 0) index = toUpIndex(index);
        if (dr > 0) index = toDownIndex(index);
        if (index == -1) return index;
        if (getBit(bitboard, index) == 1) return index;
        int nxtIndex = raycast(bitboard, index, dr, dc);
        return nxtIndex;
    }
    //Exclusive
    public static long raycastTrace(long bitboard, long current, int index, int dr, int dc)
    {
        if (dc < 0) index = toLeftIndex(index);
        if (dc > 0) index = toRightIndex(index);
        if (dr < 0) index = toUpIndex(index);
        if (dr > 0) index = toDownIndex(index);
        if (index == -1) return current;
        if (getBit(bitboard, index) == 1) return current;
        current = raycastTrace(bitboard, current, index, dr, dc);
        current = setBit(current, index);
        return current;
    }

    //From a toward b
    public static long raycastToward(long bitboard, Location a, Location b)
    {
        int dr = (b.getRow() - a.getRow());
		int dc = (b.getCol() - a.getCol());

        //Normalize to unit vectors
        dr = dr==0 ? 0 : dr/(Math.abs(dr)); 
        dc = dc==0 ? 0 : dc/(Math.abs(dc));

        return raycastTrace(bitboard, 0L, toBBIndex(a.getRow(), a.getCol()), dr, dc);
    }
    //[[Bit Operations]]\\
    public static long setBit(long bitboard, int index)
    {
        if (!isValid(index)) return bitboard; //Out of bounds
        return bitboard |= (1L << index);
    }

    public static long unsetBit(long bitboard, int index)
    {
        if (!isValid(index)) return bitboard; //Out of bounds
        return bitboard &= ~(1L << index);
    }

    public static int getBit(long bitboard, int index)
    {
        return (bitboard & (1L << index)) != 0 ? 1 : 0;
    }
    public static int getLS1F(long bitboard) {
        return bruijnIndex64[(int)(((bitboard ^ (bitboard - 1)) * 0x03f79d71b4cb0a89L) >>> 58)];
    }

    //[[Piece Manipulation]]\\
    public void addPiece(int pieceEnum, Color color, Location loc)
    {
        int row = loc.getRow(); int col = loc.getCol();
        int index = toBBIndex(row, col);

        //Update bitboards
        pieceBB[pieceEnum] = setBit(pieceBB[pieceEnum], index);
        colorBB[color.equals(Color.WHITE) ? 0 : 1] = setBit(colorBB[color.equals(Color.WHITE) ? 0 : 1], index);

        //Reset caches
        totalAttackSet[0] = totalAttackSet[1] = 0;
    }

    public void removePiece(int pieceEnum, Color color, Location loc)
    {
        int row = loc.getRow(); int col = loc.getCol();
        int index = toBBIndex(row, col);

        //Update bitboards
        pieceBB[pieceEnum] = unsetBit(pieceBB[pieceEnum], index);
        colorBB[color.equals(Color.WHITE) ? 0 : 1] = unsetBit(colorBB[color.equals(Color.WHITE) ? 0 : 1], index);

        //Reset caches
        totalAttackSet[0] = totalAttackSet[1] = 0;
    }

    //[[BB Retrieval Shortcuts]]\\
    public long getPieceBB(int pieceEnum, int color)
    {
        return pieceBB[pieceEnum]&colorBB[color];
    }

    public long getPieceBB(int pieceEnum)
    {
        return pieceBB[pieceEnum];
    }

    //[[BB Print]]\\
    public static void printBB(long bitboard)
    {
        String ret = "";
        for (int i=0;i<8;i++) {
            for (int j=0;j<8;j++) {
                ret += getBit(bitboard, toBBIndex(i, j)) + " ";
            }
            ret += "\n";
        }
        System.out.println(ret);
    }

    //[[Magic Number Generation]]\\
    public static int randomSeed = 1696969420;
    public static int generateRandom32BitNumber()
    {
        int x = randomSeed;
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        randomSeed = x;
        return x;
    }

    public static long generateRandom64BitNumber()
    {
        long x1, x2, x3, x4;
        x1 = (long)(generateRandom32BitNumber()) & 0xFFFF;
        x2 = (long)(generateRandom32BitNumber()) & 0xFFFF;
        x3 = (long)(generateRandom32BitNumber()) & 0xFFFF;
        x4 = (long)(generateRandom32BitNumber()) & 0xFFFF;

        return x1 | (x2 << 16) | (x3 << 32) | (x4 << 48);
    }

    public static long generateRandomMagic()
    {
        return generateRandom64BitNumber() & generateRandom64BitNumber() & generateRandom64BitNumber();
    }

    public int getMagicIndex(long magic, long blockerBoard, int bitCount)
    {
        return (int)((blockerBoard * magic) >>> (64 - bitCount));
    }

    public long findMagicNumber(long blockerMask, int index, int bitCount, ArrayList<Long> blockerBoardSet, ArrayList<Long> moveBoardSet) {
        int totVariations = blockerBoardSet.size();
        long[] attackSet = new long[totVariations];
        boolean fail;
        for (int rnd=0;rnd<100000000;rnd++) {
            long magic = generateRandomMagic();

            //Check if magic is inapprioriate
            if (blockerMask * magic <= 0 || Long.bitCount((blockerMask * magic) & 0xFF00000000000000L) < 6) continue;

            //Reset data structures
            Arrays.fill(attackSet, 0L);
            fail = false;

            for (int i=0;i<totVariations;i++) {
                long blockerBoard = blockerBoardSet.get(i);
                int magicIndex = getMagicIndex(magic, blockerBoard, bitCount);
                
                
                //If index not taken, assign it
                if (attackSet[magicIndex] == 0L) attackSet[magicIndex] = moveBoardSet.get(i);

                //Else if it doesn't match the same move board, then this number fails
                else if (attackSet[magicIndex] != moveBoardSet.get(i)) {
                    fail = true; break;
                }
            }

            if (!fail) return magic;
        }
        System.out.println("<<<<<<< MAGIC NUMBER GENERATION FAILED!! >>>>>>>");
        return 0;
    }

    //[[Attack Set Generation]]\\

    /*
     * To get moves, simply take all pieces of type, pop_LSF each one and get the index, then do
     * curAttackSet = (attackSet[index]&~pieces[color]) 
     * 
     * For pawns specifically, must do curAttackSet = (attackSet[index][1][dir]&pieces[oppositeColor])
     */


    public void generatePawnAttackSet()
    {
        for (int index=0;index<64;index++) {
            for (int dir=0;dir<2;dir++) {
                //<<Moves>>\\
                //Normal 1 up moves
                pawnAttackSet[index][0][dir] = setBit(pawnAttackSet[index][0][dir], dir == 0 ? toUpIndex(index) : toDownIndex(index));
                //2 up moves will be added to move set in generation

                //<<Attacks>>\\
                pawnAttackSet[index][1][dir] = setBit(pawnAttackSet[index][1][dir], dir == 0 ? toUpIndex(toLeftIndex(index)) : toDownIndex(toLeftIndex(index)));
                pawnAttackSet[index][1][dir] = setBit(pawnAttackSet[index][1][dir], dir == 0 ? toUpIndex(toRightIndex(index)) : toDownIndex(toRightIndex(index)));
            }
        }
    }
    public void generateKnightAttackSet()
    {
        for (int index=0;index<64;index++) {
            //<<Moves/Attacks>>\\
            knightAttackSet[index] = setBit(knightAttackSet[index], toUpIndex(toUpIndex(toRightIndex(index)))); //RUU
            knightAttackSet[index] = setBit(knightAttackSet[index], toUpIndex(toUpIndex(toLeftIndex(index)))); //LUU
            knightAttackSet[index] = setBit(knightAttackSet[index], toRightIndex(toRightIndex(toUpIndex(index)))); //URR
            knightAttackSet[index] = setBit(knightAttackSet[index], toRightIndex(toRightIndex(toDownIndex(index)))); //DRR
            knightAttackSet[index] = setBit(knightAttackSet[index], toLeftIndex(toLeftIndex(toUpIndex(index)))); //ULL
            knightAttackSet[index] = setBit(knightAttackSet[index], toLeftIndex(toLeftIndex(toDownIndex(index)))); //DLL
            knightAttackSet[index] = setBit(knightAttackSet[index], toDownIndex(toDownIndex(toRightIndex(index)))); //RDD
            knightAttackSet[index] = setBit(knightAttackSet[index], toDownIndex(toDownIndex(toLeftIndex(index)))); //LDD
        }
    }

    public void generateBishopAttackSet()
    {
        for (int index=0;index<64;index++) {
            long blockerMask = 0L;

            //<<Blocker Masks>>\\
            int sweepIndex;
            //Sweep UR
            sweepIndex = toUpIndex(toRightIndex(index));
            while (toUpIndex(toRightIndex(sweepIndex)) != -1) {
                blockerMask = setBit(blockerMask, sweepIndex);
                sweepIndex = toUpIndex(toRightIndex(sweepIndex));
            }
            //Sweep DR
            sweepIndex = toDownIndex(toRightIndex(index));
            while (toDownIndex(toRightIndex(sweepIndex)) != -1) {
                blockerMask = setBit(blockerMask, sweepIndex);
                sweepIndex = toDownIndex(toRightIndex(sweepIndex));
            }
            //Sweep UL
            sweepIndex = toUpIndex(toLeftIndex(index));
            while (toUpIndex(toLeftIndex(sweepIndex)) != -1) {
                blockerMask = setBit(blockerMask, sweepIndex);
                sweepIndex = toUpIndex(toLeftIndex(sweepIndex));
            }
            //Sweep DL
            sweepIndex = toDownIndex(toLeftIndex(index));
            while (toDownIndex(toLeftIndex(sweepIndex)) != -1) {
                blockerMask = setBit(blockerMask, sweepIndex);
                sweepIndex = toDownIndex(toLeftIndex(sweepIndex));
            }

            bishopBlockerMasks[index] = blockerMask;
            bishopBitCount[index] = Long.bitCount(blockerMask);

            //<<Blocker Generation>>\\
            ArrayList<Long> blockerBoardSet = new ArrayList<>();
            ArrayList<Long> moveBoardSet = new ArrayList<>();
            ArrayList<Integer> maskIndices = new ArrayList<>();
            for (int i=0;i<64;i++) {
                if (getBit(blockerMask, i) == 1) maskIndices.add(i);
            }
            
            for (int k=0;k<(int)Math.pow(2, maskIndices.size());k++) {
                long blockerBoard = 0L;
                for (int i=0;i<maskIndices.size();i++) {
                    if ((k & (1 << i)) > 0) {
                        blockerBoard = setBit(blockerBoard, maskIndices.get(i));
                    }
                }
                blockerBoardSet.add(blockerBoard);

                //<<Move Board>>\\
                long moveBoard = 0L;
                int checkIndex;

                //Sweep UR
                checkIndex = toUpIndex(toRightIndex(index));
                while (checkIndex != -1 && getBit(blockerBoard, checkIndex) != 1) {
                    moveBoard = setBit(moveBoard, checkIndex);
                    checkIndex = toUpIndex(toRightIndex(checkIndex));
                }
                moveBoard = setBit(moveBoard, checkIndex);

                //Sweep DR
                checkIndex = toDownIndex(toRightIndex(index));
                while (checkIndex != -1 && getBit(blockerBoard, checkIndex) != 1) {
                    moveBoard = setBit(moveBoard, checkIndex);
                    checkIndex = toDownIndex(toRightIndex(checkIndex));
                }
                moveBoard = setBit(moveBoard, checkIndex);

                //Sweep UL
                checkIndex = toUpIndex(toLeftIndex(index));
                while (checkIndex != -1 && getBit(blockerBoard, checkIndex) != 1) {
                    moveBoard = setBit(moveBoard, checkIndex);
                    checkIndex = toUpIndex(toLeftIndex(checkIndex));
                }
                moveBoard = setBit(moveBoard, checkIndex);

                //Sweep DL
                checkIndex = toDownIndex(toLeftIndex(index));
                while (checkIndex != -1 && getBit(blockerBoard, checkIndex) != 1) {
                    moveBoard = setBit(moveBoard, checkIndex);
                    checkIndex = toDownIndex(toLeftIndex(checkIndex));
                }
                moveBoard = setBit(moveBoard, checkIndex);

                moveBoardSet.add(moveBoard);

            }

            //<<Magic Number Generation>>\\
            bishopMagicNumbers[index] = findMagicNumber(blockerMask, index, bishopBitCount[index], blockerBoardSet, moveBoardSet);
            for (int i=0;i<blockerBoardSet.size();i++) {
                bishopAttackSet[index][getMagicIndex(bishopMagicNumbers[index], blockerBoardSet.get(i), bishopBitCount[index])] = moveBoardSet.get(i);
            }
        }
    }

    //https://stackoverflow.com/questions/16925204/sliding-move-generation-using-magic-bitboard
    public void generateRookAttackSet()
    {
        for (int index=0;index<64;index++) {
            long blockerMask = 0L;

            //<<Blocker Masks>>\\
            int sweepIndex;
            //Sweep UP
            sweepIndex = toUpIndex(index);
            while (toUpIndex(sweepIndex) != -1) {
                blockerMask = setBit(blockerMask, sweepIndex);
                sweepIndex = toUpIndex(sweepIndex);
            }
            //Sweep DOWN
            sweepIndex = toDownIndex(index);
            while (toDownIndex(sweepIndex) != -1) {
                blockerMask = setBit(blockerMask, sweepIndex);
                sweepIndex = toDownIndex(sweepIndex);
            }
            //Sweep RIGHT
            sweepIndex = toRightIndex(index);
            while (toRightIndex(sweepIndex) != -1) {
                blockerMask = setBit(blockerMask, sweepIndex);
                sweepIndex = toRightIndex(sweepIndex);
            }
            //Sweep LEFT
            sweepIndex = toLeftIndex(index);
            while (toLeftIndex(sweepIndex) != -1) {
                blockerMask = setBit(blockerMask, sweepIndex);
                sweepIndex = toLeftIndex(sweepIndex);
            }

            rookBlockerMasks[index] = blockerMask;
            rookBitCount[index] = Long.bitCount(blockerMask);

            //<<Blocker Generation>>\\
            ArrayList<Long> blockerBoardSet = new ArrayList<>();
            ArrayList<Long> moveBoardSet = new ArrayList<>();
            ArrayList<Integer> maskIndices = new ArrayList<>();
            for (int i=0;i<64;i++) {
                if (getBit(blockerMask, i) == 1) maskIndices.add(i);
            }

            for (int k=0;k<(int)Math.pow(2, maskIndices.size());k++) {
                long blockerBoard = 0L;
                for (int i=0;i<maskIndices.size();i++) {
                    if ((k & (1 << i)) > 0) {
                        blockerBoard = setBit(blockerBoard, maskIndices.get(i));
                    }
                }
                blockerBoardSet.add(blockerBoard);

                //<<Move Board>>\\
                long moveBoard = 0L;
                int checkIndex;
                //Sweep UP
                checkIndex = toUpIndex(index);
                while (checkIndex != -1 && getBit(blockerBoard, checkIndex) != 1) {
                    moveBoard = setBit(moveBoard, checkIndex);
                    checkIndex = toUpIndex(checkIndex);
                }
                moveBoard = setBit(moveBoard, checkIndex);
                //Sweep DOWN
                checkIndex = toDownIndex(index);
                while (checkIndex != -1 && getBit(blockerBoard, checkIndex) != 1) {
                    moveBoard = setBit(moveBoard, checkIndex);
                    checkIndex = toDownIndex(checkIndex);
                }
                moveBoard = setBit(moveBoard, checkIndex);
                //Sweep RIGHT
                checkIndex = toRightIndex(index);
                while (checkIndex != -1 && getBit(blockerBoard, checkIndex) != 1) {
                    moveBoard = setBit(moveBoard, checkIndex);
                    checkIndex = toRightIndex(checkIndex);
                }
                moveBoard = setBit(moveBoard, checkIndex);
                //Sweep LEFT
                checkIndex = toLeftIndex(index);
                while (checkIndex != -1 && getBit(blockerBoard, checkIndex) != 1) {
                    moveBoard = setBit(moveBoard, checkIndex);
                    checkIndex = toLeftIndex(checkIndex);
                }
                moveBoard = setBit(moveBoard, checkIndex);
                moveBoardSet.add(moveBoard);
            }

            //<<Magic Number Generation>>\\
            rookMagicNumbers[index] = findMagicNumber(blockerMask, index, rookBitCount[index], blockerBoardSet, moveBoardSet);
            for (int i=0;i<blockerBoardSet.size();i++) {
                rookAttackSet[index][getMagicIndex(rookMagicNumbers[index], blockerBoardSet.get(i), rookBitCount[index])] = moveBoardSet.get(i);
            }
        }
    }
    public void generateKingAttackSet()
    {
        for (int index=0;index<64;index++) {
            //<<Moves/Attacks>>\\
            kingAttackSet[index] = setBit(kingAttackSet[index], toUpIndex(toRightIndex(index))); //RU
            kingAttackSet[index] = setBit(kingAttackSet[index], toUpIndex(toLeftIndex(index))); //LU
            kingAttackSet[index] = setBit(kingAttackSet[index], toDownIndex(toRightIndex(index))); //RD
            kingAttackSet[index] = setBit(kingAttackSet[index], toDownIndex(toLeftIndex(index))); //LD
            kingAttackSet[index] = setBit(kingAttackSet[index], toUpIndex(index)); //U
            kingAttackSet[index] = setBit(kingAttackSet[index], toDownIndex(index)); //D
            kingAttackSet[index] = setBit(kingAttackSet[index], toLeftIndex(index)); //L
            kingAttackSet[index] = setBit(kingAttackSet[index], toRightIndex(index)); //R
        }
    }
    
    //[[Move Bitboards]]\\

    /*
     * Almost exact same code as the following section (Move Generation), except this is just
     * for getting the attack BB (with taking own pieces because technically that spot is still attacked)
     */
    public long getPawnAttackBB(Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1; int dir = rawColor.equals(Game.NEAR_COLOR) ? 0 : 1;
        long pawnBB = pieceBB[1]&colorBB[color];
        long totMoveBB = 0L;

        while (pawnBB != 0) {
            //Get piece position
            int index = getLS1F(pawnBB); pawnBB = unsetBit(pawnBB, index);

            //Add to total move bitboard
            totMoveBB |= pawnAttackSet[index][1][dir];
        }
        return totMoveBB;
    }

    public long getKnightAttackBB(Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long knightBB = pieceBB[2]&colorBB[color];
        long totMoveBB = 0L;

        while (knightBB != 0) {
            //Get piece position
            int index = getLS1F(knightBB); knightBB = unsetBit(knightBB, index);

            //Add to total move bitboard
            totMoveBB |= knightAttackSet[index];
        }
        return totMoveBB;
    }
    
    public long getBishopAttackBB(Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long bishopBB = pieceBB[3]&colorBB[color];
        long totMoveBB = 0L;

        while (bishopBB != 0) {
            //Get piece position
            int index = getLS1F(bishopBB); bishopBB = unsetBit(bishopBB, index);

            //Add to total move bitboard
            totMoveBB |= bishopAttackSet[index][getMagicIndex(bishopMagicNumbers[index], bishopBlockerMasks[index]&(colorBB[0]|colorBB[1]), bishopBitCount[index])];
        }
        return totMoveBB;
    }

    public long getRookAttackBB(Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long rookBB = pieceBB[4]&colorBB[color];
        long totMoveBB = 0L;

        while (rookBB != 0) {
            //Get piece position
            int index = getLS1F(rookBB); rookBB = unsetBit(rookBB, index);
            
            //Add to total move bitboard
            totMoveBB |= rookAttackSet[index][getMagicIndex(rookMagicNumbers[index], rookBlockerMasks[index]&(colorBB[0]|colorBB[1]), rookBitCount[index])];
        }
        return totMoveBB;
    }

    public long getQueenAttackBB(Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long queenBB = pieceBB[5]&colorBB[color];
        long totMoveBB = 0L;

        while (queenBB != 0) {
            //Get piece position
            int index = getLS1F(queenBB); queenBB = unsetBit(queenBB, index);

            //Get destinations (moves/attacks, just rook attacks & bishop attacks)
            long rookMoveSet = rookAttackSet[index][getMagicIndex(rookMagicNumbers[index], rookBlockerMasks[index]&(colorBB[0]|colorBB[1]), rookBitCount[index])];
            long bishopMoveSet = bishopAttackSet[index][getMagicIndex(bishopMagicNumbers[index], bishopBlockerMasks[index]&(colorBB[0]|colorBB[1]), bishopBitCount[index])];
            long moveSet = (rookMoveSet|bishopMoveSet);

            //Add to total move bitboard
            totMoveBB |= moveSet;
        }
        return totMoveBB;
    }

    public long getKingAttackBB(Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long kingBB = pieceBB[6]&colorBB[color];
        long totMoveBB = 0L;

        while (kingBB != 0) {
            //Get piece position
            int index = getLS1F(kingBB); kingBB = unsetBit(kingBB, index);

            //Add to total move bitboard
            totMoveBB |= kingAttackSet[index];
        }
        return totMoveBB;
    }

    //Gets the attack BB of any SINGLE piece
    public long getPieceAttackBB(int row, int col, int pieceEnum, int color)
    {
        //Get piece position
        int index = toBBIndex(row, col);

        //Get destinations (moves/attacks)
        long moveSet = 0L;
        if (pieceEnum == 1) {
            int dir = (Game.NEAR_COLOR.equals(Color.WHITE) ? 0 : 1) == color ? 0 : 1;
            moveSet = pawnAttackSet[index][1][dir];
        }
        else if (pieceEnum == 2) moveSet = knightAttackSet[index];
        else if (pieceEnum == 3) moveSet = bishopAttackSet[index][getMagicIndex(bishopMagicNumbers[index], bishopBlockerMasks[index]&(colorBB[0]|colorBB[1]), bishopBitCount[index])];
        else if (pieceEnum == 4) moveSet = rookAttackSet[index][getMagicIndex(rookMagicNumbers[index], rookBlockerMasks[index]&(colorBB[0]|colorBB[1]), rookBitCount[index])];
        else if (pieceEnum == 5) {
            long rookMoveSet = rookAttackSet[index][getMagicIndex(rookMagicNumbers[index], rookBlockerMasks[index]&(colorBB[0]|colorBB[1]), rookBitCount[index])];
            long bishopMoveSet = bishopAttackSet[index][getMagicIndex(bishopMagicNumbers[index], bishopBlockerMasks[index]&(colorBB[0]|colorBB[1]), bishopBitCount[index])];
            moveSet = rookMoveSet|bishopMoveSet;
        }
        else if (pieceEnum == 6) moveSet = kingAttackSet[index];

        return moveSet;
    }

    //[[Move Bitboard Generation]]\\

    /*
     * Generates the move bitboards in LS1F order
     */

    public ArrayList<Long>[] generatePawnMoves(Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1; int dir = rawColor.equals(Game.NEAR_COLOR) ? 0 : 1;
        long pawnBB = pieceBB[1]&colorBB[color];
        ArrayList<Long>[] moves = new ArrayList[2]; for (int i=0;i<2;i++) moves[i] = new ArrayList<>();

        while (pawnBB != 0) {
            //Get piece position
            int index = getLS1F(pawnBB); pawnBB = unsetBit(pawnBB, index);

            //Get destinations (move and attack)
            long moveSet = (pawnAttackSet[index][0][dir]&~colorBB[0]&~colorBB[1]); //Don't move/attack own pieces (for pawn, also can't take by move)
            //Two-up (only if on correct rank, and moveSet has a bit [can move 1 forward])
            if ((dir == 0 && toRow(index) == 6 || dir == 1 && toRow(index) == 1) && moveSet != 0) {
                moveSet = setBit(moveSet, dir == 0 ? toUpIndex(toUpIndex(index)) : toDownIndex(toDownIndex(index)));
                moveSet = moveSet&~colorBB[0]&~colorBB[1];
            }
            long attackSet = (pawnAttackSet[index][1][dir]&~colorBB[color]); //Don't move/attack own pieces
            attackSet = attackSet&colorBB[1-color]; //Only diagonal attack if enemy piece exists there

            moves[0].add(moveSet); moves[1].add(attackSet);
        }
        return moves;
    }

    public ArrayList<Long> generateKnightMoves(Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long knightBB = pieceBB[2]&colorBB[color];
        ArrayList<Long> moves = new ArrayList<>();

        while (knightBB != 0) {
            //Get piece position
            int index = getLS1F(knightBB); knightBB = unsetBit(knightBB, index);

            //Get destinations (moves/attacks)
            long moveSet = (knightAttackSet[index]&~colorBB[color]); //Don't move/attack own pieces
            moves.add(moveSet);
        }
        return moves;
    }

    public ArrayList<Long> generateBishopMoves(Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long bishopBB = pieceBB[3]&colorBB[color];
        ArrayList<Long> moves = new ArrayList<>();

        while (bishopBB != 0) {
            //Get piece position
            int index = getLS1F(bishopBB); bishopBB = unsetBit(bishopBB, index);

            //Get destinations (moves/attacks)
            long moveSet = bishopAttackSet[index][getMagicIndex(bishopMagicNumbers[index], bishopBlockerMasks[index]&(colorBB[0]|colorBB[1]), bishopBitCount[index])];
            moveSet = (moveSet&~colorBB[color]); //Don't move/attack own pieces
            moves.add(moveSet);
        }
        return moves;
    }

    public ArrayList<Long> generateRookMoves(Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long rookBB = pieceBB[4]&colorBB[color];
        ArrayList<Long> moves = new ArrayList<>();

        while (rookBB != 0) {
            //Get piece position
            int index = getLS1F(rookBB); rookBB = unsetBit(rookBB, index);

            //Get destinations (moves/attacks)
            long moveSet = rookAttackSet[index][getMagicIndex(rookMagicNumbers[index], rookBlockerMasks[index]&(colorBB[0]|colorBB[1]), rookBitCount[index])];
            moveSet = (moveSet&~colorBB[color]); //Don't move/attack own pieces
            moves.add(moveSet);
        }
        return moves;
    }

    public ArrayList<Long> generateQueenMoves(Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long queenBB = pieceBB[5]&colorBB[color];
        ArrayList<Long> moves = new ArrayList<>();

        while (queenBB != 0) {
            //Get piece position
            int index = getLS1F(queenBB); queenBB = unsetBit(queenBB, index);

            //Get destinations (moves/attacks, just rook attacks & bishop attacks)
            long rookMoveSet = rookAttackSet[index][getMagicIndex(rookMagicNumbers[index], rookBlockerMasks[index]&(colorBB[0]|colorBB[1]), rookBitCount[index])];
            long bishopMoveSet = bishopAttackSet[index][getMagicIndex(bishopMagicNumbers[index], bishopBlockerMasks[index]&(colorBB[0]|colorBB[1]), bishopBitCount[index])];
            long moveSet = ((rookMoveSet|bishopMoveSet)&~colorBB[color]); //Don't move/attack own pieces
            moves.add(moveSet);
        }
        return moves;
    }

    public long generateKingMoves(Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long kingBB = pieceBB[6]&colorBB[color];
        
        //Get piece position
        int index = getLS1F(kingBB); kingBB = unsetBit(kingBB, index);

        //Get destinations (moves/attacks)
        long moveSet = (kingAttackSet[index]&~colorBB[color]); //Don't move/attack own pieces
        return moveSet;
        
    }
    
    private void addMoves(ArrayList<Move> allMoves, Piece p, long moveSet)
    {
        while (moveSet != 0) {
            int toIndex = getLS1F(moveSet); moveSet = unsetBit(moveSet, toIndex);
            //Check promotions
            if (p instanceof Pawn && (toRow(toIndex) == 0 || toRow(toIndex) == 7)) {
                allMoves.add(new PromotionMove(p, new Location(toRow(toIndex), toCol(toIndex))));
            }
            else {
                allMoves.add(new Move(p, new Location(toRow(toIndex), toCol(toIndex))));
            }
        }
    }
    private void addAsMovesFromBB(ArrayList<Move> allMoves, ArrayList<Long> moves, int pieceEnum, Color rawColor, long CAPTURE_MASK, long MOVE_MASK, long[] PIN_MASKS)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long pieces = pieceBB[pieceEnum]&colorBB[color];
        int i = 0;

        while (pieces != 0) {
            //Get piece position
            int index = getLS1F(pieces); pieces = unsetBit(pieces, index);
            Piece p = board.get(toLocation(index));

            long moveSet = moves.get(i) & (CAPTURE_MASK | MOVE_MASK) & PIN_MASKS[index];
            addMoves(allMoves, p, moveSet);
            i++;
        } 
    }

    private void addAsCapturesFromBB(ArrayList<Move> allMoves, ArrayList<Long> moves, int pieceEnum, Color rawColor, long CAPTURE_MASK, long MOVE_MASK, long[] PIN_MASKS)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long pieces = pieceBB[pieceEnum]&colorBB[color];
        int i = 0;

        while (pieces != 0) {
            //Get piece position
            int index = getLS1F(pieces); pieces = unsetBit(pieces, index);
            Piece p = board.get(toLocation(index));

            //Simply use enemy pieces as mask (also include promotions for pawns)
            long moveSet = moves.get(i) & (CAPTURE_MASK | MOVE_MASK) & PIN_MASKS[index] & (colorBB[1-color] | (pieceEnum == 1 ? 0xFF000000000000FFL : 0L)); 
            addMoves(allMoves, p, moveSet);
            i++;
        } 
    }

    private void addAsChecksFromBB(ArrayList<Move> allMoves, ArrayList<Long> moves, int pieceEnum, Color rawColor, long CAPTURE_MASK, long MOVE_MASK, long[] PIN_MASKS, long[] CHECK_MASKS)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long pieces = pieceBB[pieceEnum]&colorBB[color];
        int i = 0;

        while (pieces != 0) {
            //Get piece position
            int index = getLS1F(pieces); pieces = unsetBit(pieces, index);
            Piece p = board.get(toLocation(index));

            long moveSet = moves.get(i) & (CAPTURE_MASK | MOVE_MASK) & PIN_MASKS[index]
            & CHECK_MASKS[
                pieceEnum==1&&(toRow(index)==1&&rawColor.equals(Game.NEAR_COLOR)
                ||toRow(index)==6&&!rawColor.equals(Game.NEAR_COLOR))
                ?5:pieceEnum]; //Use check mask (assumes promotion is auto-queen)

            addMoves(allMoves, p, moveSet);
            i++;
        } 
    }
    private int countMovesFromBB(ArrayList<Long> moves, int pieceEnum, Color rawColor, long CAPTURE_MASK, long MOVE_MASK, long[] PIN_MASKS)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        long pieces = pieceBB[pieceEnum]&colorBB[color];
        int i = 0; int moveCnt = 0;

        while (pieces != 0) {
            //Get piece position
            int index = getLS1F(pieces); pieces = unsetBit(pieces, index);
            Piece p = board.get(toLocation(index));

            long moveSet = moves.get(i) & (CAPTURE_MASK | MOVE_MASK) & PIN_MASKS[index];
            moveCnt += Long.bitCount(moveSet);
            i++;
        }
        return moveCnt;
    }

    public void generateAllLegalMoves(ArrayList<Move> allMoves, Color rawColor, boolean onlyCaptures, boolean onlyChecks)
    {
        //Basic Variables
        Color oppColor = Board.oppositeColor(rawColor);
        King king = board.getKing(rawColor);
        Location kingLoc = king.getLocation();
        long attackers = getAllAttackersAsBB(kingLoc, oppColor);
        int attackerCount = Long.bitCount(attackers);
        long[] pinned = getPinnedRays(rawColor);
        
        //Masks
        long CAPTURE_MASK = 0xFFFFFFFFFFFFFFFFL;
        long MOVE_MASK = 0xFFFFFFFFFFFFFFFFL;

        //King moves
        king.removeSelfFromGrid();
        generateTotalAttackSet(oppColor); //Make sure to generate totalAttackSet for opposing color
        long attackedBB = totalAttackSet[oppColor.equals(Color.WHITE) ? 0 : 1];
        king.putSelfInGrid(board, kingLoc);
        long kingMoves = generateKingMoves(rawColor) & ~attackedBB;

        ArrayList<Long> moves = new ArrayList<>(); moves.add(kingMoves);

        //Add king moves (king can't check if onlyChecks = true)
        if (onlyCaptures) {
            addAsCapturesFromBB(allMoves, moves, 6, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
        }
        else if (!onlyChecks) {
            addAsMovesFromBB(allMoves, moves, 6, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
        }
        
        //Single check
        if (attackerCount == 1) {
            CAPTURE_MASK = attackers; //Can only get out of check with a capture by capturing that piece
            Location attackerLoc = toLocation(getLS1F(attackers));
            Piece p = board.get(attackerLoc);
            if (!(p instanceof Pawn || p instanceof Knight)) {
                MOVE_MASK = raycastToward(attackers, kingLoc, attackerLoc);
            }
            else {
                MOVE_MASK = 0;
            }

        }
        //Double check+ (can only move king)
        else if (attackerCount >= 2) {
            return;
        }
        //Regular pieces
        ArrayList<Long>[] pawnMoves = generatePawnMoves(rawColor);
        ArrayList<Long> knightMoves = generateKnightMoves(rawColor);
        ArrayList<Long> bishopMoves = generateBishopMoves(rawColor);
        ArrayList<Long> rookMoves = generateRookMoves(rawColor);
        ArrayList<Long> queenMoves = generateQueenMoves(rawColor);
        
        if (onlyCaptures) {
            addAsCapturesFromBB(allMoves, pawnMoves[0], 1, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
            addAsCapturesFromBB(allMoves, pawnMoves[1], 1, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
            addAsCapturesFromBB(allMoves, knightMoves, 2, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
            addAsCapturesFromBB(allMoves, bishopMoves, 3, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
            addAsCapturesFromBB(allMoves, rookMoves, 4, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
            addAsCapturesFromBB(allMoves, queenMoves, 5, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
        }
        else if (onlyChecks) {
            long[] CHECK_MASKS = generateChecksMask(board.getKing(oppColor).getLocation(), rawColor);
            addAsChecksFromBB(allMoves, pawnMoves[0], 1, rawColor, CAPTURE_MASK, MOVE_MASK, pinned, CHECK_MASKS);
            addAsChecksFromBB(allMoves, pawnMoves[1], 1, rawColor, CAPTURE_MASK, MOVE_MASK, pinned, CHECK_MASKS);
            addAsChecksFromBB(allMoves, knightMoves, 2, rawColor, CAPTURE_MASK, MOVE_MASK, pinned, CHECK_MASKS);
            addAsChecksFromBB(allMoves, bishopMoves, 3, rawColor, CAPTURE_MASK, MOVE_MASK, pinned, CHECK_MASKS);
            addAsChecksFromBB(allMoves, rookMoves, 4, rawColor, CAPTURE_MASK, MOVE_MASK, pinned, CHECK_MASKS);
            addAsChecksFromBB(allMoves, queenMoves, 5, rawColor, CAPTURE_MASK, MOVE_MASK, pinned, CHECK_MASKS);
        }
        else {
            addAsMovesFromBB(allMoves, pawnMoves[0], 1, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
            addAsMovesFromBB(allMoves, pawnMoves[1], 1, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
            addAsMovesFromBB(allMoves, knightMoves, 2, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
            addAsMovesFromBB(allMoves, bishopMoves, 3, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
            addAsMovesFromBB(allMoves, rookMoves, 4, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
            addAsMovesFromBB(allMoves, queenMoves, 5, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
        }
        
    }

    public int countLegalMoves(Color rawColor)
    {
        //Basic Variables
        Color oppColor = Board.oppositeColor(rawColor);
        King king = board.getKing(rawColor);
        Location kingLoc = king.getLocation();
        long attackers = getAllAttackersAsBB(kingLoc, oppColor);
        int attackerCount = Long.bitCount(attackers);
        long[] pinned = getPinnedRays(rawColor);
        int totalMoveCnt = 0;
        
        //Masks
        long CAPTURE_MASK = 0xFFFFFFFFFFFFFFFFL;
        long MOVE_MASK = 0xFFFFFFFFFFFFFFFFL;

        //King moves
        king.removeSelfFromGrid();
        generateTotalAttackSet(oppColor); //Make sure to generate totalAttackSet for opposing color
        long attackedBB = totalAttackSet[oppColor.equals(Color.WHITE) ? 0 : 1];
        king.putSelfInGrid(board, kingLoc);
        long kingMoves = generateKingMoves(rawColor) & ~attackedBB;
        
        ArrayList<Long> moves = new ArrayList<>(); moves.add(kingMoves);

        //Add king moves (king can't check if onlyChecks = true)
        totalMoveCnt += countMovesFromBB(moves, 6, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
        
        //Single check
        if (attackerCount == 1) {
            CAPTURE_MASK = attackers; //Can only get out of check with a capture by capturing that piece
            Location attackerLoc = toLocation(getLS1F(attackers));
            Piece p = board.get(attackerLoc);
            if (!(p instanceof Pawn || p instanceof Knight)) {
                MOVE_MASK = raycastToward(attackers, kingLoc, attackerLoc);
            }
            else {
                MOVE_MASK = 0;
            }

        }
        //Double check+ (can only move king)
        else if (attackerCount >= 2) {
            return totalMoveCnt;
        }
        //Regular pieces
        ArrayList<Long>[] pawnMoves = generatePawnMoves(rawColor);
        ArrayList<Long> knightMoves = generateKnightMoves(rawColor);
        ArrayList<Long> bishopMoves = generateBishopMoves(rawColor);
        ArrayList<Long> rookMoves = generateRookMoves(rawColor);
        ArrayList<Long> queenMoves = generateQueenMoves(rawColor);

        totalMoveCnt += countMovesFromBB(pawnMoves[0], 1, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
        totalMoveCnt += countMovesFromBB(pawnMoves[1], 1, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
        totalMoveCnt += countMovesFromBB(knightMoves, 2, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
        totalMoveCnt += countMovesFromBB(bishopMoves, 3, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
        totalMoveCnt += countMovesFromBB(rookMoves, 4, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);
        totalMoveCnt += countMovesFromBB(queenMoves, 5, rawColor, CAPTURE_MASK, MOVE_MASK, pinned);

        return totalMoveCnt;
    }

    //[[General Board Functions]]\\

    public void generateTotalAttackSet(Color color)
    {
        int c = color.equals(Color.WHITE) ? 0 : 1;
        if (totalAttackSet[c] == 0) totalAttackSet[c] = getPawnAttackBB(color) | getKnightAttackBB(color) | getBishopAttackBB(color) | getRookAttackBB(color) | getQueenAttackBB(color) | getKingAttackBB(color);
    }
    public boolean isAttacked(Location loc, Color color)
    {
        generateTotalAttackSet(color);
        int index = toBBIndex(loc.getRow(), loc.getCol()); int c = color.equals(Color.WHITE) ? 0 : 1;
        return getBit(totalAttackSet[c], index) == 1;
    }

    public boolean isAttackedBy(Location loc, Piece piece)
    {
        int targetIndex = toBBIndex(loc.getRow(), loc.getCol());
        
        //Check if location is attacked by specific piece
        return getBit(getPieceAttackBB(piece.getLocation().getRow(), piece.getLocation().getCol(), piece.getEnum(), piece.getColor().equals(Color.WHITE) ? 0 : 1), targetIndex) == 1;
    }

    public long getAllAttackersAsBB(Location loc, Color rawColor)
    {
        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        int row = loc.getRow(); int col = loc.getCol();
        
        //Piece checks
        long pawns = getPieceAttackBB(row, col,1, 1-color) & getPieceBB(1, color); //Pawns intersect with opposite color pawn attacks
        long knights = getPieceAttackBB(row, col, 2, 1-color) & getPieceBB(2, color);
        long bishops = getPieceAttackBB(row, col, 3, 1-color) & getPieceBB(3, color);
        long rooks = getPieceAttackBB(row, col, 4, 1-color) & getPieceBB(4, color);
        long queens = getPieceAttackBB(row, col, 5, 1-color) & getPieceBB(5, color);
        long kings = getPieceAttackBB(row, col, 6, 1-color) & getPieceBB(6, color);

        long allPieces = pawns | knights | bishops | rooks | queens | kings;
        return allPieces;
    }

    public long[] generateChecksMask(Location loc, Color rawColor)
    {
        long[] checksMask = new long[7];

        int color = rawColor.equals(Color.WHITE) ? 0 : 1;
        int row = loc.getRow(); int col = loc.getCol();
        
        //Piece checks
        checksMask[1] = getPieceAttackBB(row, col,1, 1-color);
        checksMask[2] = getPieceAttackBB(row, col, 2, 1-color);
        checksMask[3] = getPieceAttackBB(row, col, 3, 1-color);
        checksMask[4] = getPieceAttackBB(row, col, 4, 1-color);
        checksMask[5] = getPieceAttackBB(row, col, 5, 1-color);
        checksMask[6] = getPieceAttackBB(row, col, 6, 1-color);
        return checksMask;
    }
    
    public ArrayList<Piece>[] getAllAttackers(Location loc, Color rawColor)
    {
        ArrayList<Piece>[] attackers = new ArrayList[7]; for (int pieceEnum=1;pieceEnum<=6;pieceEnum++) attackers[pieceEnum] = new ArrayList<>();

        long allPieces = getAllAttackersAsBB(loc, rawColor);
        while (allPieces != 0) {
            int pieceIndex = getLS1F(allPieces); allPieces = unsetBit(allPieces, pieceIndex);
            Piece p = board.get(toRow(pieceIndex), toCol(pieceIndex));
            attackers[p.getEnum()].add(p);
        } 
        return attackers;
    }

    public long[] getPinnedRays(Color rawColor)
    {
        long[] pinned = new long[64]; Arrays.fill(pinned, ~(0L));

        Location kingLoc = board.getKing(rawColor).getLocation();
        int index = toBBIndex(kingLoc.getRow(), kingLoc.getCol());
        long fullBB = colorBB[0] | colorBB[1];

        int[] dr = {-1,-1,-1,1,1,1,0,0};
        int[] dc = {-1,0,1,-1,0,1,-1,1};
        for (int k=0;k<8;k++) {
            int kingRay = raycast(fullBB, index, dr[k], dc[k]);
            if (kingRay != -1) {
                Piece p = board.get(toLocation(kingRay));
                if (p.getColor().equals(rawColor)) {
                    int pinnerRay = raycast(fullBB, kingRay, dr[k], dc[k]);
                    if (pinnerRay != -1) {
                        Piece pinner = board.get(toRow(pinnerRay),toCol(pinnerRay));
                        if (pinner.getColor().equals(Board.oppositeColor(rawColor))
                        && (dr[k]*dc[k]==0 && (pinner instanceof Rook || pinner instanceof Queen) || dr[k]*dc[k]!=0 && (pinner instanceof Bishop || pinner instanceof Queen))) {
                            //Lies on a sliding ray in between own king and enemy piece, so is a pinned piece
                            pinned[kingRay] = raycastToward(fullBB, toLocation(kingRay), kingLoc) | raycastToward(fullBB, toLocation(kingRay), toLocation(pinnerRay));
                            pinned[kingRay] = setBit(pinned[kingRay], pinnerRay); //Can also capture pinner
                        }
                        
                    }
                }
            }
        }
        return pinned;
    }
}
