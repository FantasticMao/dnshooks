package cn.fantasticmao.dnshooks.proxy;

import cn.fantasticmao.dnshooks.proxy.disruptor.*;
import cn.fantasticmao.dnshooks.proxy.netty.DnsProxyClient;
import cn.fantasticmao.dnshooks.proxy.netty.DnsProxyDatagramClient;
import cn.fantasticmao.dnshooks.proxy.netty.DnsProxyServer;
import cn.fantasticmao.dnshooks.proxy.netty.DnsServerAddressUtil;
import cn.fantasticmao.dnshooks.proxy.util.Constant;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Main
 *
 * @author maomao
 * @since 2020-03-11
 */
@Slf4j
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.write(readBanner("/banner.txt"));

        // use blocking wait strategy，avoid to cost a lot of CPU resources
        final WaitStrategy waitStrategy = new BlockingWaitStrategy();
        final Disruptor<DnsMessage> disruptor = new Disruptor<>(DnsMessageFactory.INSTANCE,
            Constant.RINGBUFFER_SIZE, HookThreadFactory.INSTANCE, ProducerType.MULTI, waitStrategy);
        // register all DNS hooks as event handler to Disruptor
        DnsMessageHook[] handlers = loadHooks().toArray(new DnsMessageHook[0]);
        disruptor.handleEventsWith(handlers);
        disruptor.setDefaultExceptionHandler(new DnsMessageExceptionHandler());
        disruptor.start();
        log.trace("start Disruptor/Ring Buffer success");

        final InetSocketAddress proxyServerAddress = new InetSocketAddress(53);
        log.trace("DNSHooks-Proxy bind local address: {}", proxyServerAddress);

        final InetSocketAddress dnsServerAddress = choseDnsServerAddress();
        log.trace("chose DNS Server Address: {}", dnsServerAddress);

        final DnsProxyClient client = new DnsProxyDatagramClient(proxyServerAddress, dnsServerAddress);
        final DnsProxyServer server = new DnsProxyServer(proxyServerAddress, client, disruptor);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    log.trace("trigger JVM shutdown hook");
                    server.close();
                    client.close();
                    disruptor.shutdown(1000, TimeUnit.MILLISECONDS);
                    for (DnsMessageHook hook : handlers) {
                        hook.close();
                    }
                } catch (Exception e) {
                    log.error("catch an exception in JVM shutdown hook", e);
                }
            }
        }));
    }

    static InetSocketAddress choseDnsServerAddress() {
        // TODO filter 127.0.0.1:53 && chose DNS server address
        List<InetSocketAddress> dnsServerAddressList = DnsServerAddressUtil.listRawDnsServerAddress();
        return dnsServerAddressList.get(0);
    }

    /**
     * use ServiceLoader to find all DNS hooks
     *
     * @return {@link DnsMessageHook} list
     */
    static List<DnsMessageHook> loadHooks() {
        List<DnsMessageHook> handlerList = new LinkedList<>();
        ServiceLoader.load(DnsMessageHook.class).forEach(handlerList::add);
        if (log.isTraceEnabled()) {
            log.trace("load all DnsMessageHook: {}", handlerList.stream()
                .map(DnsMessageHook::name)
                .collect(Collectors.toList()));
        }
        return handlerList;
    }

    static byte[] readBanner(String banner) throws URISyntaxException, IOException {
        final Path path = Paths.get(Main.class.getResource(banner).toURI());
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        } else {
            log.trace("{} file does not exists", path);
            return new byte[0];
        }
    }
}
