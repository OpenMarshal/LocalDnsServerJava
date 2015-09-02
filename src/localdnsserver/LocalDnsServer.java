package localdnsserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.util.Scanner;

/**
 * @author Adrien Castex
 */
public class LocalDnsServer
{
    public static void main(String[] args) throws SocketException, IOException, InterruptedException
    {
        DnsServer server = DnsServer.create()
                .addForbiddenDomainRegexFromFile(createNewFilterFile(new File("filter.txt")))
                .setOnPassed(d -> System.out.println(" >>> " + d))
                .setOnBlocked(d -> System.out.println(" >X> " + d + " [BLOCKED]"))
                .setOnError(e -> System.err.println("ERROR : " + e.getMessage()))
                .build();
        
        server.toThread()
                .start();
        
        Thread.sleep(300);
        
        if(!server.isRunning())
        {
            System.out.println("STOPPED : Error or timeout occured.");
            server.stop();
            return;
        }
        else
            System.out.println("STARTED");
        
        Scanner scan = new Scanner(System.in);
        String line;
        
        do
        {
            line = scan.nextLine();
        } while(line == null || !line.trim().toLowerCase().equals("exit"));
        
        server.stop();
    }
    
    /**
     * Creates a new filter file if it doesn't exist.
     * @param filterFile File to create.
     */
    private static File createNewFilterFile(File filterFile)
    {
        if(!filterFile.exists())
        {
            String lineSeparator = System.getProperty("line.separator");
            try(
                    BufferedWriter writer = new BufferedWriter(new FileWriter(filterFile));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(LocalDnsServer.class.getResourceAsStream("filter.txt")));
                )
            {
                writer.write(reader.lines().reduce("", (s1, s2) -> s1.length() > 0 ? s1 + lineSeparator + s2 : s2));
            }
            catch(IOException ex)
            {
                ex.printStackTrace();
            }
        }
        
        return filterFile;
    }
}
