// Copyright (c) Microsoft and contributors.  All rights reserved.
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

package smb.sdk.sample.common;

import com.azure.resourcemanager.netapp.fluent.NetAppManagementClient;
import com.azure.resourcemanager.netapp.fluent.models.*;

// Contains public methods for SDK related operations
public class CommonSdk
{
    /**
     * Returns an ANF resource or null if it does not exist
     * @param anfClient Azure NetApp Files Management Client
     * @param parameters List of parameters required depending on the resource type:
     *                   Account        -> ResourceGroupName, AccountName
     *                   Capacity Pool  -> ResourceGroupName, AccountName, PoolName
     *                   Volume         -> ResourceGroupName, AccountName, PoolName, VolumeName
     *                   Snapshot       -> ResourceGroupName, AccountName, PoolName, VolumeName, SnapshotName
     * @param clazz Valid class types: NetAppAccountInner, CapacityPoolInner, VolumeInner, SnapshotInner
     * @return Valid resource T
     */
    public static <T> Object getResource(NetAppManagementClient anfClient, String[] parameters, Class<T> clazz)
    {
        try
        {
            switch (clazz.getSimpleName())
            {
                case "NetAppAccountInner":
                    return anfClient.getAccounts().getByResourceGroup(
                            parameters[0],
                            parameters[1]);

                case "SnapshotPolicyInner":
                    return anfClient.getSnapshotPolicies().get(
                            parameters[0],
                            parameters[1],
                            parameters[2]);

                case "CapacityPoolInner":
                    return anfClient.getPools().get(
                            parameters[0],
                            parameters[1],
                            parameters[2]);

                case "VolumeInner":
                    return anfClient.getVolumes().get(
                            parameters[0],
                            parameters[1],
                            parameters[2],
                            parameters[3]);

                case "SnapshotInner":
                    return anfClient.getSnapshots().get(
                            parameters[0],
                            parameters[1],
                            parameters[2],
                            parameters[3],
                            parameters[4]);
            }
        }
        catch (Exception e)
        {
            if (e.getMessage().contains("Status code 404"))
                return null;
            Utils.writeWarningMessage("Error finding resource - " + e.getMessage());
        }

        return null;
    }

    /**
     * Method to overload function waitForNoANFResource(client, string, int, int, clazz) with default values
     * @param anfClient Azure NetApp Files Management Client
     * @param resourceId Resource id of the resource that was deleted
     * @param clazz Valid class types: NetAppAccountInner, CapacityPoolInner, VolumeInner, SnapshotInner
     */
    public static <T> void waitForNoANFResource(NetAppManagementClient anfClient, String resourceId, Class<T> clazz)
    {
        waitForNoANFResource(anfClient, resourceId, 10, 60, clazz);
    }

    /**
     * This function checks if a specific ANF resource that was recently deleted stops existing. It breaks the wait
     * if the resource is not found anymore or if polling reached its maximum retries.
     * @param anfClient Azure NetApp Files Management Client
     * @param resourceId Resource id of the resource that was deleted
     * @param intervalInSec Time in second that the function will poll to see if the resource has been deleted
     * @param retries Number of times polling will be performed
     * @param clazz Valid class types: NetAppAccountInner, CapacityPoolInner, VolumeInner, SnapshotInner
     */
    public static <T> void waitForNoANFResource(NetAppManagementClient anfClient, String resourceId, int intervalInSec, int retries, Class<T> clazz)
    {
        for (int i = 0; i < retries; i++)
        {
            Utils.threadSleep(intervalInSec*2000);

            try
            {
                switch (clazz.getSimpleName())
                {
                    case "NetAppAccountInner":
                        NetAppAccountInner account = anfClient.getAccounts().getByResourceGroup(ResourceUriUtils.getResourceGroup(resourceId),
                                ResourceUriUtils.getAnfAccount(resourceId));
                        if (account == null)
                            return;

                        continue;

                    case "CapacityPoolInner":
                        CapacityPoolInner pool = anfClient.getPools().get(ResourceUriUtils.getResourceGroup(resourceId),
                                ResourceUriUtils.getAnfAccount(resourceId),
                                ResourceUriUtils.getAnfCapacityPool(resourceId));
                        if (pool == null)
                            return;

                        continue;

                    case "VolumeInner":
                        VolumeInner volume = anfClient.getVolumes().get(ResourceUriUtils.getResourceGroup(resourceId),
                                ResourceUriUtils.getAnfAccount(resourceId),
                                ResourceUriUtils.getAnfCapacityPool(resourceId),
                                ResourceUriUtils.getAnfVolume(resourceId));
                        if (volume == null)
                            return;

                        continue;

                    case "SnapshotInner":
                        SnapshotInner snapshot = anfClient.getSnapshots().get(ResourceUriUtils.getResourceGroup(resourceId),
                                ResourceUriUtils.getAnfAccount(resourceId),
                                ResourceUriUtils.getAnfCapacityPool(resourceId),
                                ResourceUriUtils.getAnfVolume(resourceId),
                                ResourceUriUtils.getAnfSnapshot(resourceId));
                        if (snapshot == null)
                            return;
                }
            }
            catch (Exception e)
            {
                Utils.writeWarningMessage(e.getMessage());
                break;
            }
        }
    }
}
