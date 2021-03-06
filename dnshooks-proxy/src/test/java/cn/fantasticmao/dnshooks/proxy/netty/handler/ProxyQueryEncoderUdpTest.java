package cn.fantasticmao.dnshooks.proxy.netty.handler;

import cn.fantasticmao.dnshooks.proxy.netty.AttributeKeyConstant;
import cn.fantasticmao.dnshooks.proxy.netty.DnsProtocolTest;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;

/**
 * ProxyQueryEncoderUdpTest
 *
 * @author maomao
 * @since 2020-03-25
 */
@Slf4j
public class ProxyQueryEncoderUdpTest extends DnsProtocolTest {

    @Test
    public void unitTest() throws Exception {
        final DatagramDnsQuery dnsQuery = super.newUdpQuery(super.dnsClientAddress, super.proxyServerAddress);
        final ProxyQueryEncoder.Udp queryEncoder = new ProxyQueryEncoder.Udp(super.dnsServerAddress);
        final EmbeddedChannel embeddedChannel = new EmbeddedChannel(queryEncoder);

        embeddedChannel.writeOutbound(dnsQuery);
        Assert.assertTrue(embeddedChannel.finish());
        Assert.assertNotNull(embeddedChannel.readOutbound());

        final InetSocketAddress rawSender = embeddedChannel.attr(AttributeKeyConstant.RAW_SENDER).get();
        Assert.assertEquals(super.dnsClientAddress, rawSender);
        final DatagramDnsQuery queryAfter = (DatagramDnsQuery) embeddedChannel
            .attr(AttributeKeyConstant.QUERY_AFTER).get();
        Assert.assertEquals(super.dnsServerAddress, queryAfter.recipient());
    }

}