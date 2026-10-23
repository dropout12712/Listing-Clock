package net.listingclock.core;
//

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerGateTest {
    @ParameterizedTest @ValueSource(strings = {"donutsmp.net", "DONUTSMP.NET", "play.donutsmp.net", "eu.donutsmp.net:25565", "donutsmp.net.", " donutsmp.net:1 ", "donutsmp.net:65535"})
    void acceptsOnlyDomainAndRealSubdomains(String address) { assertTrue(ServerGate.allows(address, false)); }

    @ParameterizedTest @ValueSource(strings = {"", "localhost", "127.0.0.1", "donutsmp.net.evil.com", "fakedonutsmp.net", "notdonutsmp.net", "donutsmp.net@evil.com", "https://donutsmp.net", "donutsmp.net:0", "donutsmp.net:65536", "donutsmp.net:abc", "evil.com/donutsmp.net", "-bad.donutsmp.net", "x..donutsmp.net"})
    void rejectsLookalikesAndInvalidAddresses(String address) { assertFalse(ServerGate.allows(address, false)); }

    @Test void rejectsSingleplayerAndNull() {
        assertFalse(ServerGate.allows("donutsmp.net", true));
        assertFalse(ServerGate.allows(null, false));
    }
}
