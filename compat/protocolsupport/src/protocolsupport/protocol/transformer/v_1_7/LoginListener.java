package protocolsupport.protocol.transformer.v_1_7;

import me.elier.minecraft.util.CryptException;
import net.minecraft.server.v1_8_R3.NetworkManager;
import protocolsupport.protocol.transformer.handlers.AbstractLoginListener;

import javax.crypto.SecretKey;

/** WindSpigot compatibility replacement for the CursedPS 1.7 login listener. */
public class LoginListener extends AbstractLoginListener {
    public LoginListener(NetworkManager networkManager) {
        super(networkManager);
    }

    @Override
    protected boolean hasCompression() {
        return false;
    }

    @Override
    protected void enableEncryption(SecretKey key) {
        try {
            networkManager.setupEncryption(key);
        } catch (CryptException exception) {
            throw new IllegalStateException("Could not enable WindSpigot encryption", exception);
        }
    }
}
