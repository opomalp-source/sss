import java.awt.*;import java.awt.image.BufferedImage;import javax.imageio.ImageIO;import java.io.File;
/** Original geometric space-suit textures and icons. No source images or external assets. */
public class PlanetArt {
 static void rect(Graphics2D g,int c,int x,int y,int w,int h){g.setColor(new Color(c,true));g.fillRect(x,y,w,h);}
 static void write(BufferedImage im,String path)throws Exception{File f=new File(path);f.getParentFile().mkdirs();ImageIO.write(im,"png",f);}
 public static void main(String[] args)throws Exception{
  for(int layer=1;layer<=2;layer++){
   var im=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);var g=im.createGraphics();
   rect(g,0xffc7d7df,0,0,64,32);rect(g,0xff879dab,0,16,64,2);
   for(int x=0;x<64;x+=8){rect(g,0xff667a8b,x,18,1,14);rect(g,0xffeef5f3,x+1,18,1,14);}
   rect(g,0xff24465c,8,9,8,5);rect(g,0xff71e4e8,9,10,6,2);rect(g,0xffe5b75f,8,14,8,1);
   rect(g,0xff193649,21,22,6,7);rect(g,0xff66cbd2,22,23,4,2);rect(g,0xffefbd69,23,27,2,1);
   rect(g,0xff536c7f,29,21,6,10);rect(g,0xfff1f4e8,30,22,4,8);rect(g,0xff24465c,0,28,16,4);
   rect(g,0xff24465c,40,28,16,4);
   // The armor model includes the head's outer hat layer: leave it transparent so it cannot hide the visor.
   g.setComposite(AlphaComposite.Clear);g.fillRect(32,0,32,16);
   g.dispose();write(im,"src/main/resources/assets/dbzenith/textures/models/armor/space_suit_layer_"+layer+".png");
  }
  String[] ids={"helmet","chestplate","leggings","boots"};
  for(int i=0;i<4;i++){
   var im=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);var g=im.createGraphics();
   if(i==0){rect(g,0xff1b3449,6,3,20,23);rect(g,0xffdae6e7,8,4,16,23);rect(g,0xff24465c,9,10,14,8);rect(g,0xff79d5dd,11,11,10,3);rect(g,0xffe6b664,12,23,8,2);}
   if(i==1){rect(g,0xff1b3449,3,4,26,22);rect(g,0xffdbe7e8,5,5,22,20);rect(g,0,12,4,8,6);rect(g,0xff24465c,10,14,12,8);rect(g,0xff77cdd7,12,15,8,3);rect(g,0xffedb66c,14,20,4,2);}
   if(i==2){rect(g,0xff1b3449,7,4,18,24);rect(g,0xffcbdde1,8,5,16,22);rect(g,0,14,15,4,14);rect(g,0xff24465c,8,6,16,3);rect(g,0xff7db9ce,9,21,4,3);rect(g,0xff7db9ce,19,21,4,3);}
   if(i==3){rect(g,0xff24465c,4,10,10,18);rect(g,0xff24465c,18,10,10,18);rect(g,0xffdbe6e6,5,11,8,11);rect(g,0xffdbe6e6,19,11,8,11);rect(g,0xff62b8c7,4,24,10,2);rect(g,0xff62b8c7,18,24,10,2);}
   g.dispose();write(im,"src/main/resources/assets/dbzenith/textures/item/space_suit_"+ids[i]+".png");
  }
 }
}
