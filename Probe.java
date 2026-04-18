import org.bukkit.Material;
import org.bukkit.block.Furnace;
import java.util.Map;
public class Probe {
  void test(Furnace furnace, Material material) {
    short a = furnace.getBurnTime();
    int b = furnace.getCookTimeTotal();
    Map<?,?> c = furnace.getRecipesUsed();
    int d = material.getBurnTime();
  }
}
