package usvm.hack;

import org.usvm.memory.UMemoryRegionId;
import org.usvm.model.UModelBase;

import java.util.Set;

public class UModelBaseAccess {
    private UModelBaseAccess() {}

    // hack: access model internal field [regions]
    public static <T> Set<UMemoryRegionId<?, ?>> modelRegions(
        final UModelBase<T> model
    ) {
        return model.getRegions().keySet();
    }
}
