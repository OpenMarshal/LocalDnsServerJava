package localdnsserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * This class represents the DNS server.
 *
 * @author Adrien Castex
 */
public class DnsServer implements Runnable
{
    public DnsServer(
            Collection<String> filteredAddrs,
            InetAddress defaultAddress,
            int defaultPort,
            boolean blockAll,
            Consumer<String> onBlocked,
            Consumer<String> onPassed,
            Consumer<String> onReceived,
            Consumer<Exception> onError,
            int exitTimeCheck)
    {
        this.filteredAddrs = filteredAddrs;
        this.defaultAddress = defaultAddress;
        this.defaultPort = defaultPort;
        this.blockAll = blockAll;
        this.onBlocked = onBlocked;
        this.onPassed = onPassed;
        this.onReceived = onReceived;
        this.exitTimeCheck = exitTimeCheck;
        this.onError = onError;
    }
    
    public static Builder create()
    {
        return new Builder();
    }
    public static class Builder
    {
        public Builder()
        { }
        
        private final Collection<String> filteredAddrs = new ConcurrentLinkedQueue<>();
        private InetAddress defaultAddress = null;
        private int defaultPort = 53;
        private boolean blockAll = false;
        private Consumer<String> onBlocked = null;
        private Consumer<String> onPassed = null;
        private Consumer<String> onReceived = null;
        private Consumer<Exception> onError = null;
        private int exitTimeCheck = -1;
        
        public Builder setOnBlocked(Consumer<String> onBlocked)
        {
            this.onBlocked = onBlocked;
            
            return this;
        }
        public Builder setOnPassed(Consumer<String> onPassed)
        {
            this.onPassed = onPassed;
            
            return this;
        }
        public Builder setOnReceived(Consumer<String> onReceived)
        {
            this.onReceived = onReceived;
            
            return this;
        }
        public Builder setOnError(Consumer<Exception> onError)
        {
            this.onError = onError;
            
            return this;
        }
        
        public Builder setDefaultAddress(InetAddress address)
        {
            defaultAddress = address;
            
            return this;
        }
        public Builder setDefaultAddress(String address)
        {
            try
            {
                defaultAddress = InetAddress.getByAddress(convertIP(address));
            }
            catch (UnknownHostException ex)
            { }
            
            return this;
        }
        
        public Builder setDefaultPort(int port)
        {
            defaultPort = port;
            
            return this;
        }
        
        public Builder setBlockAll(boolean value)
        {
            blockAll = value;
            
            return this;
        }
        
        
        public Builder addForbiddenDomainRegexFromFile(String filterFile) throws IOException
        {
            addForbiddenDomainRegexFromFile(new File(filterFile));
            
            return this;
        }
        public Builder addForbiddenDomainRegexFromFile(File filterFile) throws IOException
        {
            addForbiddenDomainRegexFromFile(filterFile.toPath());
            
            return this;
        }

        private Stream<String> cleanFilters(Stream<String> stream)
        {
            return stream
                    .filter(l -> l != null)
                    .map(l -> (l.contains("#") ? l.substring(0, l.indexOf("#")) : l))
                    .map(String::trim)
                    .filter(l -> !l.isEmpty());
        }
        public Builder addForbiddenDomainRegexFromFile(Path filterFile) throws IOException
        {
            cleanFilters(Files.readAllLines(filterFile)
                    .stream())
                    .flatMap(l ->
                    {
                        if(l.toLowerCase().startsWith("include"))
                            return cleanFilters(getIncludeFilterFile(l.substring("include".length()).trim()).stream());
                        return Stream.of(new String[] { l });
                    })
                    .forEach(this::addForbiddenDomainRegex);
            
            return this;
        }
        public Builder addForbiddenDomainRegex(String domainRegex)
        {
            domainRegex = domainRegex.replace("*", ".*");

            filteredAddrs.add(domainRegex);
            
            return this;
        }
        private static Collection<String> getIncludeFilterFile(String target)
        {
            try
            {
                File filterFile = new File(target);
                if(filterFile.exists())
                    return Files.readAllLines(filterFile.toPath());
            }
            catch(IOException ex)
            { }

            HttpURLConnection connection = null;
            try
            {
                connection = (HttpURLConnection)new URL(target).openConnection();

                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Length", "0"); 

                connection.setUseCaches(false);
                connection.setDoOutput(true);

                Collection<String> lines = new LinkedList<>();
                try(BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream())))
                {
                    String line;
                    while((line = rd.readLine()) != null)
                    {
                        lines.add(line);
                    }
                }
                return lines;
            }
            catch(Exception e)
            {
                e.printStackTrace();
                return Collections.EMPTY_LIST;
            }
            finally
            {
                if(connection != null)
                  connection.disconnect(); 
            }
        }
        
        public Builder setExitTimeCheck(int exitTimeCheck)
        {
            this.exitTimeCheck = exitTimeCheck;
            
            return this;
        }
        
        public DnsServer build()
        {
            if(defaultAddress == null)
                setDefaultAddress("212.27.40.241");
            if(defaultPort <= 0)
                setDefaultPort(53);
            if(exitTimeCheck <= 0)
                exitTimeCheck = 3000;
            
            return new DnsServer(
                    filteredAddrs,
                    defaultAddress,
                    defaultPort,
                    blockAll,
                    onBlocked,
                    onPassed,
                    onReceived,
                    onError,
                    exitTimeCheck);
        }
    }
    
    
    // <editor-fold defaultstate="collapsed" desc="Fields">
    private final Collection<String> filteredAddrs;
    private final InetAddress defaultAddress;
    private final int defaultPort;
    private final boolean blockAll;
    private final int exitTimeCheck;
    
    private boolean stop;
    private boolean running = false;
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Static methods">
    public static byte[] convertIP(String ip)
    {
        Integer[] array = Stream.of(ip.split("\\."))
                .map(Integer::parseInt)
                .toArray(Integer[]::new);
        
        return new byte[] { (byte)(int)array[0], (byte)(int)array[1], (byte)(int)array[2], (byte)(int)array[3] };
    }
    public static byte[] convertDomain(String domain)
    {
        byte[] data = new byte[domain.length()];
        
        for(int i = 0; i < domain.length(); i++)
        {
            if(domain.charAt(i) == '.')
                data[i] = 0;
            else
                data[i] = (byte)domain.charAt(i);
        }
        
        return data;
    }
    public static String toString(byte[] data, int start, int end)
    {
        String url = "";
        for(int i = start; i < end; i++)
        {
            if(data[i] <= '#')
                url += '.';
            else
                url += (char)data[i];
        }
        return url;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Methods / Accessors">
    public void stop()
    {
        this.stop = true;
    }
    
    public boolean isRunning()
    {
        return this.running;
    }
    
    public Thread toThread()
    {
        return new Thread(this);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Events">
    private final Consumer<String> onPassed;
    private final Consumer<String> onBlocked;
    private final Consumer<String> onReceived;
    private final Consumer<Exception> onError;
    
    private <T> void invoke(Consumer<T> event, T value)
    {
        try
        {
            if(event != null)
                event.accept(value);
        }
        catch(Exception ex)
        {
            System.out.println("ERROR : " + ex.getMessage());
        }
    }
    // </editor-fold>

    @Override
    public synchronized void run()
    {
        try(
                DatagramSocket sendSocket = new DatagramSocket();
                DatagramSocket socket = new DatagramSocket(53);
            )
        {
            socket.setSoTimeout(exitTimeCheck);
            
            byte[] datas = new byte[500];
            
            DatagramPacket packet = new DatagramPacket(datas, datas.length);

            stop = false;
            running = true;
            
            while(!stop)
            {
                try
                {
                    socket.receive(packet);

                    InetAddress addr = packet.getAddress();
                    int port = packet.getPort();

                    String domain = toString(datas, 13, packet.getLength() - 4 - 1);

                    boolean blocked = blockAll || filteredAddrs.stream().anyMatch(domain::matches);
                        
                    invoke(onReceived, domain);

                    if(!blocked)
                    { // allowed
                        sendSocket.send(new DatagramPacket(datas, packet.getLength(), defaultAddress, defaultPort));
                        sendSocket.receive(packet);
                        socket.send(new DatagramPacket(datas, packet.getLength(), addr, port));
                        
                        invoke(onPassed, domain);
                    }
                    else
                    { // blocked
                        datas[2] = (byte)0x81;
                        datas[3] = (byte)0x83;
                        datas[6] = (byte)0x00;
                        datas[7] = (byte)0x00;
                        socket.send(new DatagramPacket(datas, packet.getLength(), addr, port));
                        
                        invoke(onBlocked, domain);
                    }
                }
                catch(SocketTimeoutException ex)
                { }
            }
        }
        catch (SocketException | UnknownHostException ex)
        {
            invoke(onError, ex);
        }
        catch (IOException ex)
        {
            invoke(onError, ex);
        }
        finally
        {
            running = false;
        }
    }
}
