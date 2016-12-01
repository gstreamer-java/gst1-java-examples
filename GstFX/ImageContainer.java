import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

public class ImageContainer {
	private ObjectProperty<Image> image = new SimpleObjectProperty<Image>();


	public Image getImage() {
		return image.get();
	}

	public void setImage(Image image) {
		this.image.set(image);
	}
	
	public void addListener(ChangeListener<Image> listener){
		image.addListener(listener);
	}
}
