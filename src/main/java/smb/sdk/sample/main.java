// Copyright (c) Microsoft and contributors.  All rights reserved.
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

package smb.sdk.sample;

import com.azure.core.credential.TokenCredential;
import com.azure.core.exception.AzureException;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.netapp.NetAppFilesManager;
import com.azure.resourcemanager.netapp.fluent.NetAppManagementClient;
import com.azure.resourcemanager.netapp.fluent.models.CapacityPoolInner;
import com.azure.resourcemanager.netapp.fluent.models.NetAppAccountInner;
import com.azure.resourcemanager.netapp.fluent.models.VolumeInner;
import com.azure.resourcemanager.netapp.models.ActiveDirectory;
import com.azure.resourcemanager.netapp.models.ServiceLevel;
import smb.sdk.sample.common.CommonSdk;
import smb.sdk.sample.common.Utils;

import java.util.Collections;

public class main
{
    /**
     * Sample console application that executes CRUD management operations on Azure NetApp Files resources
     * The volume created will use the SMB/CIFS protocol
     * @param args
     */
    public static void main( String[] args )
    {
        Utils.displayConsoleAppHeader();

        try
        {
            run();
            Utils.writeConsoleMessage("Sample application successfully completed execution");
        }
        catch (Exception e)
        {
            Utils.writeErrorMessage(e.getMessage());
        }

        System.exit(0);
    }

    private static void run()
    {
        //---------------------------------------------------------------------------------------------------------------------
        // Setting variables necessary for resources creation - change these to appropriate values related to your environment
        //---------------------------------------------------------------------------------------------------------------------
        boolean cleanup = false;

        String subscriptionId = "<subscription-id>";
        String location = "<location>";
        String resourceGroupName = "<resource-group-name>";
        String vnetName = "<vnet-name>";
        String subnetName = "<subnet-name>";
        String anfAccountName = "anf-java-example-account";
        String capacityPoolName = "anf-java-example-pool";
        String capacityPoolServiceLevel = "Standard"; // Valid service levels are: Ultra, Premium, Standard
        String volumeName = "anf-java-example-volume";

        long capacityPoolSize = 4398046511104L;  // 4TiB which is minimum size
        long volumeSize = 107374182400L;  // 100GiB - volume minimum size

        // SMB/CIFS related variables
        String domainJoinUsername = "testadmin";
        String dnsList = "10.0.2.4,10.0.2.5"; // Please notice that this is a comma-separated string
        String adFQDN = "testdomain.local";
        String smbServerNamePrefix = "testsmb"; // this needs to be maximum 10 characters in length and during the domain join process a random string gets appended.

        // Instantiating a new ANF management client and authenticate
        AzureProfile profile = new AzureProfile(AzureEnvironment.AZURE);
        TokenCredential credential = new DefaultAzureCredentialBuilder()
                .authorityHost(profile.getEnvironment().getActiveDirectoryEndpoint())
                .build();
        Utils.writeConsoleMessage("Instantiating a new Azure NetApp Files management client...");
        NetAppFilesManager manager = NetAppFilesManager
                .authenticate(credential, profile);

        //------------------------------------------------------------------------------------------------------
        // Getting Active Directory Identity's password (from identity that has rights to domain join computers)
        //------------------------------------------------------------------------------------------------------
        System.out.println("Please type Active Directory's user password that will domain join ANF's SMB server and press [ENTER]:");
        String domainJoinUserPassword = Utils.getConsolePassword();

        //------------------------
        // Creating ANF resources
        //------------------------

        //----------------------
        // Create ANF Account
        //----------------------
        Utils.writeConsoleMessage("Creating Azure NetaApp Files Account...");

        String[] accountParams = {resourceGroupName, anfAccountName};
        NetAppAccountInner anfAccount = (NetAppAccountInner) CommonSdk.getResource(manager.serviceClient(), accountParams, NetAppAccountInner.class);
        if (anfAccount == null)
        {
            // Setting up Active Directories Object
            ActiveDirectory activeDirectory = new ActiveDirectory();
            activeDirectory.withUsername(domainJoinUsername);
            activeDirectory.withPassword(domainJoinUserPassword);
            activeDirectory.withDns(dnsList);
            activeDirectory.withDomain(adFQDN);
            activeDirectory.withSmbServerName(smbServerNamePrefix);

            NetAppAccountInner newAccount = new NetAppAccountInner();
            newAccount.withLocation(location);
            newAccount.withActiveDirectories(Collections.singletonList(activeDirectory));

            try
            {
                anfAccount = createANFAccount(manager.serviceClient(), resourceGroupName, anfAccountName, newAccount);
            }
            catch (AzureException e)
            {
                Utils.writeConsoleMessage("An error occurred while creating account: " + e.getMessage());
                throw  e;
            }
        }
        else
        {
            Utils.writeConsoleMessage("Account already exists");
        }

        //----------------------
        // Create Capacity Pool
        //----------------------
        Utils.writeConsoleMessage("Creating Capacity Pool...");

        String[] poolParams = {resourceGroupName, anfAccountName, capacityPoolName};
        CapacityPoolInner capacityPool = (CapacityPoolInner) CommonSdk.getResource(manager.serviceClient(), poolParams, CapacityPoolInner.class);
        if (capacityPool == null)
        {
            CapacityPoolInner newCapacityPool = new CapacityPoolInner();
            newCapacityPool.withServiceLevel(ServiceLevel.fromString(capacityPoolServiceLevel));
            newCapacityPool.withSize(capacityPoolSize);
            newCapacityPool.withLocation(location);

            try
            {
                capacityPool = createCapacityPool(manager.serviceClient(), resourceGroupName, anfAccountName, capacityPoolName, newCapacityPool);
            }
            catch (Exception e)
            {
                Utils.writeConsoleMessage("An error occurred while creating capacity pool: " + e.getMessage());
                throw e;
            }
        }
        else
        {
            Utils.writeConsoleMessage("Capacity Pool already exists");
        }

        //------------------------
        // Create SMB Volume
        //------------------------
        Utils.writeConsoleMessage("Creating SMB Volume...");

        String[] volumeParams = {resourceGroupName, anfAccountName, capacityPoolName, volumeName};
        VolumeInner volume = (VolumeInner) CommonSdk.getResource(manager.serviceClient(), volumeParams, VolumeInner.class);
        if (volume == null)
        {
            String subnetId = "/subscriptions/" + subscriptionId + "/resourceGroups/" + resourceGroupName +
                    "/providers/Microsoft.Network/virtualNetworks/" + vnetName + "/subnets/" + subnetName;

            VolumeInner newVolume = new VolumeInner();
            newVolume.withLocation(location);
            newVolume.withServiceLevel(ServiceLevel.fromString(capacityPoolServiceLevel));
            newVolume.withCreationToken(volumeName);
            newVolume.withSubnetId(subnetId);
            newVolume.withUsageThreshold(volumeSize);
            newVolume.withProtocolTypes(Collections.singletonList("CIFS"));

            try
            {
                volume = createSMBVolume(manager.serviceClient(), resourceGroupName, anfAccountName, capacityPoolName, volumeName, newVolume);
            }
            catch (Exception e)
            {
                Utils.writeConsoleMessage("An error occurred while creating volume: " + e.getMessage());
                throw e;
            }
        }
        else
        {
            Utils.writeConsoleMessage("Volume already exists");
        }

        //------------------------
        // Cleaning up resources
        //------------------------

        /*
          Cleanup process. For this process to take effect please change the value of
          the boolean variable 'cleanup' to 'true'
          The cleanup process starts from the innermost resources down in the hierarchy chain.
          In this case: Volume -> Capacity Pool -> Account
        */
        if (cleanup)
        {
            // Disable Illegal Reflective Access warning
            Utils.suppressWarning();

            Utils.writeConsoleMessage("Cleaning up all created resources");

            Utils.writeConsoleMessage("Deleting Volume...");
            manager.serviceClient().getVolumes().beginDelete(resourceGroupName, anfAccountName, capacityPoolName, volumeName).getFinalResult();
            // ARM workaround to wait for the deletion to complete
            CommonSdk.waitForNoANFResource(manager.serviceClient(), volume.id(), VolumeInner.class);
            Utils.writeSuccessMessage("Volume successfully deleted: " + volume.id());

            Utils.writeConsoleMessage("Deleting Capacity Pool...");
            manager.serviceClient().getPools().beginDelete(resourceGroupName, anfAccountName, capacityPoolName).getFinalResult();
            CommonSdk.waitForNoANFResource(manager.serviceClient(), capacityPool.id(), CapacityPoolInner.class);
            Utils.writeSuccessMessage("Capacity Pool successfully deleted: " + capacityPool.id());

            Utils.writeConsoleMessage("Deleting ANF Account...");
            manager.serviceClient().getAccounts().beginDelete(resourceGroupName, anfAccountName).getFinalResult();
            CommonSdk.waitForNoANFResource(manager.serviceClient(), anfAccount.id(), NetAppAccountInner.class);
            Utils.writeSuccessMessage("ANF Account successfully deleted: " + anfAccount.id());
        }
    }


    /**
     * Creates an ANF Account
     * @param anfClient Azure NetApp Files Management Client
     * @param resourceGroup Name of the resource group where the Account will be created
     * @param accountName Name of the Account being created
     * @param accountBody The Account body used in the creation
     * @return The newly created ANF Account
     */
    private static NetAppAccountInner createANFAccount(NetAppManagementClient anfClient, String resourceGroup, String accountName, NetAppAccountInner accountBody)
    {
        NetAppAccountInner anfAccount = anfClient.getAccounts().beginCreateOrUpdate(resourceGroup, accountName, accountBody).getFinalResult();
        Utils.writeSuccessMessage("Account successfully created, resourceId: " + anfAccount.id());

        return anfAccount;
    }

    /**
     * Creates a Capacity Pool
     * @param anfClient Azure NetApp Files Management Client
     * @param resourceGroup Name of the resource group where the Account will be created
     * @param accountName Name of the Account being created
     * @param poolName Name of the Capacity Pool being created
     * @param poolBody The Capacity Pool body used in the creation
     * @return The newly created Capacity Pool
     */
    private static CapacityPoolInner createCapacityPool(NetAppManagementClient anfClient, String resourceGroup, String accountName, String poolName, CapacityPoolInner poolBody)
    {
        CapacityPoolInner capacityPool = anfClient.getPools().beginCreateOrUpdate(resourceGroup, accountName, poolName, poolBody).getFinalResult();
        Utils.writeSuccessMessage("Capacity Pool successfully created, resourceId: " + capacityPool.id());

        return capacityPool;
    }

    /**
     * Creates a Volume
     * @param anfClient Azure NetApp Files Management Client
     * @param resourceGroup Name of the resource group where the Account will be created
     * @param accountName Name of the Account being created
     * @param poolName Name of the Capacity Pool being created
     * @param volumeName Name of the Volume being created
     * @param volumeBody The Volume body used in the creation
     * @return The newly created Volume
     */
    private static VolumeInner createSMBVolume(NetAppManagementClient anfClient, String resourceGroup, String accountName, String poolName, String volumeName, VolumeInner volumeBody)
    {
        VolumeInner volume = anfClient.getVolumes().beginCreateOrUpdate(resourceGroup, accountName, poolName, volumeName, volumeBody).getFinalResult();
        Utils.writeSuccessMessage("Volume successfully created, resourceId: " + volume.id());
        Utils.writeConsoleMessage("SMB Server FQDN: " + volume.mountTargets().get(0).smbServerFqdn());

        return volume;
    }
}
