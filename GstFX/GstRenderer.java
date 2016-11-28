import java.nio.ByteOrder;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.AppSink;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class GstRenderer extends Application{
	private ImageView imageView;
	private AppSink videosink;
	private Pipeline pipe;
	private Bin bin;
	private StringBuilder caps;
	private ImageContainer imageContainer;
	public GstRenderer() {
		videosink = new AppSink("GstVideoComponent");
        videosink.set("emit-signals", true);
        AppSinkListener GstListener = new AppSinkListener();
        videosink.connect(GstListener);
        caps = new StringBuilder("video/x-raw, ");
        // JNA creates ByteBuffer using native byte order, set masks according to that.
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            caps.append("format=BGRx");
        } else {
            caps.append("format=xRGB");
        }
        videosink.setCaps(new Caps(caps.toString()));
        //?
        videosink.set("max-buffers", 5000);
        videosink.set("drop", true);
        bin = Bin.launch("autovideosrc ! videoconvert ", true);
        pipe = new Pipeline();
        pipe.addMany(bin, videosink);
        Pipeline.linkMany(bin, videosink);
        pipe.play();
        imageView = new ImageView();
        
        imageContainer = GstListener.getImageContainer();
        imageContainer.addListener(new ChangeListener<Image>() {

			@Override
			public void changed(ObservableValue<? extends Image> observable, Image oldValue,
					Image newValue) {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						imageView.setImage(newValue);
					}
				});
				
			}
        	
		});
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		primaryStage.setTitle("Drawing Operations Test");
		BorderPane grid = new BorderPane();
		grid.setCenter(imageView);
		imageView.viewportProperty().addListener(new ChangeListener<Rectangle2D>() {

			@Override
			public void changed(ObservableValue<? extends Rectangle2D> observable, Rectangle2D oldValue,
					Rectangle2D newValue) {
				System.out.println(newValue);
			}

		});
	    imageView.fitWidthProperty().bind(grid.widthProperty()); 
	    imageView.fitHeightProperty().bind(grid.heightProperty()); 
	    imageView.setPreserveRatio(true);
		primaryStage.setScene(new Scene(grid, 460, 460));
        primaryStage.show();
	}
	
	public static void main(String[] args) {
        Gst.init("CameraTest",args);
        launch(args);
    }
	
	public void print(Image image){
		imageView.setImage(image);
	}

}
