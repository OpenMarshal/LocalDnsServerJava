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
    public static void main(String[] args) throws SocketException, IOException
    {
        DnsServer server = new DnsServer();
        
        File filterFile = new File("filter.txt");
        createNewFilterFile(filterFile);
        
        server.setDiagnosisMode(true);
        server.addForbiddenDomainRegexFromFile(filterFile);
        server.toThread().start();
        
        Scanner scan = new Scanner(System.in);
        String line;
        
        do
        {
            line = scan.nextLine().trim().toLowerCase();
            if(line == null || line.length() == 0)
                continue;
            
            String[] params = line.split(" ");
            String cmd = params[0];
            
            try
            {
                switch(cmd)
                {
                    case "reload":
                        server.clearForbiddenDomainRegex();
                        server.addForbiddenDomainRegexFromFile(filterFile);
                        System.out.println("Entry file \"" + filterFile.getAbsolutePath() + "\" reloaded.");
                        break;

                    case "diagnosis":
                        if(params.length > 1)
                        {
                            server.setDiagnosisMode(params[1].equals("true"));
                            System.out.println("Diagnosis mode " + (params[1].equals("true") ? "activated" : "disactivated") + ".");
                        }
                        else
                            System.out.println("Parameter missing.");
                        break;

                    case "blockall":
                        if(params.length > 1)
                        {
                            server.setBlockAllMode(params[1].equals("true"));
                            System.out.println("Block all mode " + (params[1].equals("true") ? "activated" : "disactivated") + ".");
                        }
                        else
                            System.out.println("Parameter missing.");
                        break;

                    case "defaultip":
                        if(params.length > 1)
                        {
                            server.setDefaultAddress(params[1]);
                            System.out.println("Default ip changed to " + params[1] + ".");
                        }
                        else
                            System.out.println("Parameter missing.");
                        break;

                    case "defaultport":
                        if(params.length > 1)
                        {
                            int port = Integer.parseInt(params[1]);
                            server.setDefaultPort(port);
                            System.out.println("Default port changed to " + port + ".");
                        }
                        else
                            System.out.println("Parameter missing.");
                        break;

                    case "?":
                    case "help":
                        System.out.println("********** Help **********");
                        System.out.println("* reload : reload the file containing the entries.");
                        System.out.println("* diagnosis [start/stop] : start/stop the diagnosis.");
                        System.out.println("* blockAll [start/stop] : start/stop blocking all entries.");
                        System.out.println("* defaultip [ip] : set the default DNS server ip if the entry is valid.");
                        System.out.println("* defaultport [port] : set the default DNS server port if the entry is valid.");
                        System.out.println("* exit : stop the server.");
                        System.out.println("**************************");
                        break;
                        
                    case "exit":
                        server.stop();
                        break;

                    default:
                        System.out.println("Command \"" + cmd + "\" not found.");
                        break;
                }
            }
            catch(Exception ex)
            {
                System.out.println("An error occured while executing the command " + cmd + " [" + ex.getMessage() + "]");
            }
            
        } while(!line.equals("exit"));
    }
    
    /**
     * Creates a new filter file if it doesn't exist.
     * @param filterFile File to create.
     */
    private static void createNewFilterFile(File filterFile)
    {
        if(!filterFile.exists())
        {
            String lineSeparator = System.getProperty("line.separator");
            try(
                    BufferedWriter writer = new BufferedWriter(new FileWriter(filterFile));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(LocalDnsServer.class.getResourceAsStream("localdnsserver.filter.txt")));
                )
            {
                writer.write(reader.lines().reduce("", (s1, s2) -> s1.length() > 0 ? s1 + lineSeparator + s2 : s2));
            }
            catch(IOException ex)
            { }
        }
    }
}
