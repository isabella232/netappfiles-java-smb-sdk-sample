// Copyright (c) Microsoft and contributors.  All rights reserved.
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

package smb.sdk.sample;

import com.ea.async.Async;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.netapp.v2019_11_01.ActiveDirectory;
import com.microsoft.azure.management.netapp.v2019_11_01.ServiceLevel;
import com.microsoft.azure.management.netapp.v2019_11_01.implementation.AzureNetAppFilesManagementClientImpl;
import com.microsoft.azure.management.netapp.v2019_11_01.implementation.CapacityPoolInner;
import com.microsoft.azure.management.netapp.v2019_11_01.implementation.NetAppAccountInner;
import com.microsoft.azure.management.netapp.v2019_11_01.implementation.VolumeInner;
import com.microsoft.rest.credentials.ServiceClientCredentials;
import smb.sdk.sample.common.CommonSdk;
import smb.sdk.sample.common.ServiceCredentialsAuth;
import smb.sdk.sample.common.Utils;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static com.ea.async.Async.await;

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
            Async.init();
            runAsync();
            Utils.writeConsoleMessage("Sample application successfully completed execution");
        }
        catch (Exception e)
        {
        }

        System.exit(0);
    }

    private static CompletableFuture<Void> runAsync()
    {
        //---------------------------------------------------------------------------------------------------------------------
        // Setting variables necessary for resources creation - change these to appropriate values related to your environment
        //---------------------------------------------------------------------------------------------------------------------
        boolean cleanup = false;

        String subscriptionId = "<subscription id>";
        String location = "eastus";
        String resourceGroupName = "anf01-rg";
        String vnetName = "vnet";
        String subnetName = "anf-sn";
        String anfAccountName = "test-account01";
        String capacityPoolName = "test-pool01";
        String capacityPoolServiceLevel = "Standard";
        String volumeName = "test-vol01";

        long capacityPoolSize = 4398046511104L;  // 4TiB which is minimum size
        long volumeSize = 107374182400L;  // 100GiB - volume minimum size

        // SMB/CIFS related variables
        String domainJoinUsername = "testadmin";
        String dnsList = "10.0.2.4,10.0.2.5"; // Please notice that this is a comma-separated string
        String adFQDN = "testdomain.local";
        String smbServerNamePrefix = "testsmb"; // this needs to be maximum 10 characters in length and during the domain join process a random string gets appended.

        // Authenticating using service principal, refer to README.md file for requirement details
        ServiceClientCredentials credentials = ServiceCredentialsAuth.getServicePrincipalCredentials(System.getenv("AZURE_AUTH_LOCATION"));
        if (credentials == null)
        {
            return CompletableFuture.completedFuture(null);
        }

        // Instantiating a new ANF management client
        Utils.writeConsoleMessage("Instantiating a new Azure NetApp Files management client...");
        AzureNetAppFilesManagementClientImpl anfClient = new AzureNetAppFilesManagementClientImpl(credentials);
        anfClient.withSubscriptionId(subscriptionId);
        Utils.writeConsoleMessage("Api Version: " + anfClient.apiVersion());

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
        NetAppAccountInner anfAccount = await(CommonSdk.getResourceAsync(anfClient, accountParams, NetAppAccountInner.class));
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
                anfAccount = await(createANFAccount(anfClient, resourceGroupName, anfAccountName, newAccount));
            }
            catch (CloudException e)
            {
                Utils.writeConsoleMessage("An error occurred while creating account: " + e.body().message());
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
        CapacityPoolInner capacityPool = await(CommonSdk.getResourceAsync(anfClient, poolParams, CapacityPoolInner.class));
        if (capacityPool == null)
        {
            CapacityPoolInner newCapacityPool = new CapacityPoolInner();
            newCapacityPool.withServiceLevel(ServiceLevel.fromString(capacityPoolServiceLevel));
            newCapacityPool.withSize(capacityPoolSize);
            newCapacityPool.withLocation(location);

            try
            {
                capacityPool = await(createCapacityPool(anfClient, resourceGroupName, anfAccountName, capacityPoolName, newCapacityPool));
            }
            catch (Exception e)
            {
                Utils.writeConsoleMessage("An error occurred while creatin capacity pool: " + e.getMessage());
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
        VolumeInner volume = await(CommonSdk.getResourceAsync(anfClient, volumeParams, VolumeInner.class));
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
                volume = await(createSMBVolume(anfClient, resourceGroupName, anfAccountName, capacityPoolName, volumeName, newVolume));
            }
            catch (Exception e)
            {
                Utils.writeErrorMessage("An error occurred while creating volume: " + e.getMessage());
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
            anfClient.volumes().delete(resourceGroupName, anfAccountName, capacityPoolName, volumeName);
            // ARM workaround to wait for the deletion to complete
            CommonSdk.waitForNoANFResource(anfClient, volume.id(), VolumeInner.class);
            Utils.writeSuccessMessage("Volume successfully deleted: " + volume.id());

            Utils.writeConsoleMessage("Deleting Capacity Pool...");
            anfClient.pools().delete(resourceGroupName, anfAccountName, capacityPoolName);
            CommonSdk.waitForNoANFResource(anfClient, capacityPool.id(), CapacityPoolInner.class);
            Utils.writeSuccessMessage("Capacity Pool successfully deleted: " + capacityPool.id());

            Utils.writeConsoleMessage("Waiting for 30 seconds before deleting Accounts to make sure all nested resources have been removed...");
            Utils.threadSleep(30000);

            Utils.writeConsoleMessage("Deleting ANF Account...");
            anfClient.accounts().delete(resourceGroupName, anfAccountName);
            CommonSdk.waitForNoANFResource(anfClient, anfAccount.id(), NetAppAccountInner.class);
            Utils.writeSuccessMessage("ANF Account successfully deleted: " + anfAccount.id());
        }

        return CompletableFuture.completedFuture(null);
    }


    /**
     * Creates an ANF Account
     * @param anfClient Azure NetApp Files Management Client
     * @param resourceGroup Name of the resource group where the Account will be created
     * @param accountName Name of the Account being created
     * @param accountBody The Account body used in the creation
     * @return The newly created ANF Account
     */
    private static CompletableFuture<NetAppAccountInner> createANFAccount(AzureNetAppFilesManagementClientImpl anfClient, String resourceGroup,
                                                            String accountName, NetAppAccountInner accountBody)
    {
        NetAppAccountInner anfAccount = anfClient.accounts().createOrUpdate(resourceGroup, accountName, accountBody);
        Utils.writeSuccessMessage("Account successfully created, resourceId: " + anfAccount.id());

        return CompletableFuture.completedFuture(anfAccount);
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
    private static CompletableFuture<CapacityPoolInner> createCapacityPool(AzureNetAppFilesManagementClientImpl anfClient, String resourceGroup,
                                                              String accountName, String poolName, CapacityPoolInner poolBody)
    {
        CapacityPoolInner capacityPool = anfClient.pools().createOrUpdate(resourceGroup, accountName, poolName, poolBody);
        Utils.writeSuccessMessage("Capacity Pool successfully created, resourceId: " + capacityPool.id());

        return CompletableFuture.completedFuture(capacityPool);
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
    private static CompletableFuture<VolumeInner> createSMBVolume(AzureNetAppFilesManagementClientImpl anfClient, String resourceGroup,
                                                           String accountName, String poolName, String volumeName, VolumeInner volumeBody)
    {
        VolumeInner volume = anfClient.volumes().createOrUpdate(resourceGroup, accountName, poolName, volumeName, volumeBody);
        Utils.writeSuccessMessage("Volume successfully created, resourceId: " + volume.id());
        Utils.writeConsoleMessage("SMB Server FQDN: " + volume.mountTargets().get(0).smbServerFqdn());

        return CompletableFuture.completedFuture(volume);
    }
}
