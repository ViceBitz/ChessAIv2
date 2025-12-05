import java.util.*;
public final class CommandLog extends Thread {
    public void run() {
        Scanner sc = new Scanner(System.in);
        while (true) {
            String line = sc.nextLine();
            if (line.equals("--tp")) {
                System.out.println("<<TIME PRESSURE ACTIVATED>>");
                Game.whiteAI.togglePrecisionPressure(false);
                Game.blackAI.togglePrecisionPressure(false);
            }
            else if (line.equals("--utp")) {
                System.out.println("<<TIME PRESSURE DEACTIVATED>>");
                Game.whiteAI.togglePrecisionPressure(true);
                Game.blackAI.togglePrecisionPressure(true);
            }
            else if (line.equals("--pp")) {
                System.out.println("<<PRECISION PRESSURE ACTIVATED>>");
                Game.whiteAI.togglePrecisionPressure(true);
                Game.blackAI.togglePrecisionPressure(true);
            }
            else if (line.equals("--upp")) {
                System.out.println("<<PRECISION PRESSURE DEACTIVATED>>");
                Game.whiteAI.togglePrecisionPressure(false);
                Game.blackAI.togglePrecisionPressure(false);
            }
            else if (line.equals("--ep")) {
                System.out.println("<<EXTREME PRECISION ACTIVATED>>");
                Game.whiteAI.toggleExtremePrecisionPressure(true);
                Game.blackAI.toggleExtremePrecisionPressure(true);
            }
            else if (line.equals("--uep")) {
                System.out.println("<<EXTREME PRECISION DEACTIVATED>>");
                Game.whiteAI.toggleExtremePrecisionPressure(false);
                Game.blackAI.toggleExtremePrecisionPressure(false);
            }
        }
    }
}
