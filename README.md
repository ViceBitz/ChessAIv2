## 💡 Inspiration
Extended from a Data Structures extra-credit assignment involving a basic chess engine with a “SmartPlayer” requirement

## 📚 Features

### Search
- **Negamax framework** for simpler and more consistent recursive search logic
- **Alpha–beta pruning** to eliminate unnecessary branches
- **Quiescence search** including captures, promotions, and checks (first 3 plies) to mitigate horizon effect
- **Null-move pruning** for aggressive branch reduction when a null move holds evaluation above beta
- **Late Move Reductions (LMR)** to reduce depth on low-priority moves and improve search efficiency
- **Reverse futility pruning (RMP)** in main search to skip obviously poor continuations
- **Delta pruning (futility)** in quiescence search to ignore hopeless captures

### Evaluation
- **Material-based evaluation** as core baseline
- **Piece-square tables (custom pieceTable data structure)** for positional nuance
- **Tapered evaluation** blending midgame and endgame values smoothly
- **Pawn structure analysis** including islands, isolated pawns, doubled pawns
- **King safety metrics** for early/midgame
- **Mobility scoring** to reward active play
- **Bishop pair bonuses and piece development**
- **Dynamic phase detection** to transition between MG/EG heuristics

### Move Ordering & Heuristics
- **Capture → Quiet → Bad capture ordering** for efficient alpha–beta cutoffs
- **Killer move heuristic** to prioritize moves that previously caused cutoffs
- **Countermove heuristic** to favor moves that respond well to opponent history
- Custom quiet-move heuristics:
   - Midgame: prioritize piece activity → queen moves → pawn pushes → king moves
   - Endgame: prioritize king activity → piece moves → queen moves → pawn pushes

### Engine Architecture
- Custom pieceTable evaluation structure (core to nuanced positional scoring)
- Modular design for search, evaluation, and move generation layers
- Debug logging built to track pruning decisions and missed tactical patterns
- Iterative deepening and time cutoffs on search depth

## 📈 Performance & Results
- Consistently reaches higher effective depths due to heavy pruning optimizations
- Beats ~2000-rated chess.com bots reliably
- Competitive games vs 2600+ rated bots, tactical accuracy limited by deeper horizon effects
- Significantly reduced node count after adding LMR, null-move pruning, and improved ordering
- Quiescence + check handling resolved earlier issues with missed mates and traps

