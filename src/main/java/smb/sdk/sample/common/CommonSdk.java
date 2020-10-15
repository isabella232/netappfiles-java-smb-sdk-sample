// Copyright (c) Microsoft and contributors.  All rights reserved.
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

package smb.sdk.sample.common;

import com.microsoft.azure.management.netapp.v2019_11_01.implementation.AzureNetAppFilesManagementClientImpl;
import com.microsoft.azure.management.netapp.v2019_11_01.implementation.CapacityPoolInner;
import com.microsoft.azure.management.netapp.v2019_11_01.implementation.NetAppAccountInner;
import com.microsoft.azure.management.netapp.v2019_11_01.implementation.SnapshotInner;
import com.microsoft.azure.management.netapp.v2019_11_01.implementation.VolumeInner;
import rx.Observable;

import java.util.concurrent.CompletableFuture;

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
    public static <T> CompletableFuture<T> getResourceAsync(AzureNetAppFilesManagementClientImpl anfClient, String[] parameters, Class<T> clazz)
    {
        try
        {
            switch (clazz.getSimpleName())
            {
                case "NetAppAccountInner":
                    Observable<NetAppAccountInner> account = anfClient.accounts().getByResourceGroupAsync(
                            parameters[0],
                            parameters[1]);
                    return CompletableFuture.completedFuture((T) account.toBlocking().first());

                case "CapacityPoolInner":
                    Observable<CapacityPoolInner> capacityPool = anfClient.pools().getAsync(
                            parameters[0],
                            parameters[1],
                            parameters[2]);
                    return CompletableFuture.completedFuture((T) capacityPool.toBlocking().first());

                case "VolumeInner":
                    Observable<VolumeInner> volume = anfClient.volumes().getAsync(
                            parameters[0],
                            parameters[1],
                            parameters[2],
                            parameters[3]);
                    return CompletableFuture.completedFuture((T) volume.toBlocking().first());

                case "SnapshotInner":
                    Observable<SnapshotInner> snapshot = anfClient.snapshots().getAsync(
                            parameters[0],
                            parameters[1],
                            parameters[2],
                            parameters[3],
                            parameters[4]);
                    return CompletableFuture.completedFuture((T) snapshot.toBlocking().first());
            }
        }
        catch (Exception e)
        {
            Utils.writeErrorMessage("Error finding resource - " + e.getMessage());
            throw e;
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Method to overload function waitForNoANFResource(client, string, int, int, clazz) with default values
     * @param anfClient Azure NetApp Files Management Client
     * @param resourceId Resource id of the resource that was deleted
     * @param clazz Valid class types: NetAppAccountInner, CapacityPoolInner, VolumeInner, SnapshotInner
     */
    public static <T> void waitForNoANFResource(AzureNetAppFilesManagementClientImpl anfClient, String resourceId, Class<T> clazz)
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
    public static <T> void waitForNoANFResource(AzureNetAppFilesManagementClientImpl anfClient, String resourceId, int intervalInSec, int retries, Class<T> clazz)
    {
        for (int i = 0; i < retries; i++)
        {
            Utils.threadSleep(intervalInSec*2000);

            try
            {
                switch (clazz.getSimpleName())
                {
                    case "NetAppAccountInner":
                        Observable<NetAppAccountInner> account = anfClient.accounts().getByResourceGroupAsync(ResourceUriUtils.getResourceGroup(resourceId),
                                ResourceUriUtils.getAnfAccount(resourceId));
                        if (account.toBlocking().first() == null)
                            return;

                        continue;

                    case "CapacityPoolInner":
                        Observable<CapacityPoolInner> pool = anfClient.pools().getAsync(ResourceUriUtils.getResourceGroup(resourceId),
                                ResourceUriUtils.getAnfAccount(resourceId),
                                ResourceUriUtils.getAnfCapacityPool(resourceId));
                        if (pool.toBlocking().first() == null)
                            return;

                        continue;

                    case "VolumeInner":
                        Observable<VolumeInner> volume = anfClient.volumes().getAsync(ResourceUriUtils.getResourceGroup(resourceId),
                                ResourceUriUtils.getAnfAccount(resourceId),
                                ResourceUriUtils.getAnfCapacityPool(resourceId),
                                ResourceUriUtils.getAnfVolume(resourceId));
                        if (volume.toBlocking().first() == null)
                            return;

                        continue;

                    case "SnapshotInner":
                        Observable<SnapshotInner> snapshot = anfClient.snapshots().getAsync(ResourceUriUtils.getResourceGroup(resourceId),
                                ResourceUriUtils.getAnfAccount(resourceId),
                                ResourceUriUtils.getAnfCapacityPool(resourceId),
                                ResourceUriUtils.getAnfVolume(resourceId),
                                ResourceUriUtils.getAnfSnapshot(resourceId));
                        if (snapshot.toBlocking().first() == null)
                            return;

                        continue;
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
