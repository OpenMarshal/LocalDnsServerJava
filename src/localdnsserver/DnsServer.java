/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package localdnsserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
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
import java.util.stream.Stream;

/**
 *
 * @author Adrien
 */
public class DnsServer implements Runnable
{
    public DnsServer()
    { }
    
    private Collection<String> filteredAddrs = new ConcurrentLinkedQueue<>();
    
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

    private static int getUInt(byte value)
    {
        return value & 0x000000FF;
    }
    private static int getValue(byte[] datas, int index)
    {
        return (getUInt(datas[index]) << 8) | getUInt(datas[index + 1]);
    }
    
    private InetAddress defaultAddress = null;
    public void setDefaultAddress(String ip) throws UnknownHostException
    {
        this.defaultAddress = Inet4Address.getByAddress(convertIP(ip));
    }
    
    private int defaultPort = 53;
    public void setDefaultPort(int port)
    {
        this.defaultPort = port;
    }
    
    private boolean diagnosisMode = false;
    public void setDiagnosisMode(boolean value)
    {
        diagnosisMode = value;
    }
    
    private boolean blockAll = false;
    public void setBlockAllMode(boolean value)
    {
        blockAll = value;
    }
    
    public void clearForbiddenDomainRegex()
    {
        filteredAddrs.clear();
    }
    public void addForbiddenDomainRegexFromFile(String filterFile) throws IOException
    {
        addForbiddenDomainRegexFromFile(new File(filterFile));
    }
    public void addForbiddenDomainRegexFromFile(File filterFile) throws IOException
    {
        addForbiddenDomainRegexFromFile(filterFile.toPath());
    }
    
    protected Stream<String> cleanFilters(Stream<String> stream)
    {
        return stream
                .filter(l -> l != null)
                .map(l -> (l.contains("#") ? l.substring(0, l.indexOf("#")) : l))
                .map(String::trim)
                .filter(l -> !l.isEmpty());
    }
    public void addForbiddenDomainRegexFromFile(Path filterFile) throws IOException
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
    }
    public void addForbiddenDomainRegex(String domainRegex)
    {
        domainRegex = domainRegex.replace("*", ".*");
        
        filteredAddrs.add(domainRegex);
    }
    protected static Collection<String> getIncludeFilterFile(String target)
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
    
    
    private boolean stop = false;
    public void stop()
    {
        this.stop = true;
    }
    
    public Thread toThread()
    {
        return new Thread(this);
    }

    @Override
    public void run()
    {
        try(
                DatagramSocket sendSocket = new DatagramSocket();
                DatagramSocket socket = new DatagramSocket(53);
            )
        {
            if(defaultAddress == null)
                setDefaultAddress("212.27.40.241");
            
            socket.setSoTimeout(1000);
            
            byte[] datas = new byte[500];
            
            DatagramPacket packet = new DatagramPacket(datas, datas.length);

            while(!stop)
            {
                try
                {
                    socket.receive(packet);

                    InetAddress addr = packet.getAddress();
                    int port = packet.getPort();

                    String domain = toString(datas, 13, packet.getLength() - 4 - 1);

                    boolean blocked = blockAll || filteredAddrs.stream().anyMatch(domain::matches);

                    if(diagnosisMode)
                    {
                        if(!blocked)
                            System.out.println("\u001B[37;40;1m >> " + domain);
                        else
                            System.out.println("\u001B[31;40;1m >> " + domain + " [BLOCKED]");
                        System.out.print("\u001B[0m");
                    }

                    if(!blocked)
                    { // allowed
                        sendSocket.send(new DatagramPacket(datas, packet.getLength(), defaultAddress, defaultPort));
                        sendSocket.receive(packet);
                        socket.send(new DatagramPacket(datas, packet.getLength(), addr, port));
                    }
                    else
                    { // blocked
                        datas[2] = (byte)0x81;
                        datas[3] = (byte)0x83;
                        datas[6] = (byte)0x00;
                        datas[7] = (byte)0x00;
                        socket.send(new DatagramPacket(datas, packet.getLength(), addr, port));
                    }
                }
                catch(SocketTimeoutException ex)
                { }
            }
        }
        catch (SocketException | UnknownHostException ex)
        {
            ex.printStackTrace();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }
}
