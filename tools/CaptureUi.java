import java.awt.*;
import javax.imageio.ImageIO;
import java.io.File;
/** Captures a running desktop; a visual smoke test, not user-interaction acceptance. */
public class CaptureUi {public static void main(String[] args)throws Exception {
 var size=Toolkit.getDefaultToolkit().getScreenSize();
 ImageIO.write(new Robot().createScreenCapture(new Rectangle(0,0,Math.min(size.width,1200),Math.min(size.height,820))),"png",new File(args[0]));
}}
