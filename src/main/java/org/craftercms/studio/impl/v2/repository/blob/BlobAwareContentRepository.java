/*
 * Copyright (C) 2007-2020 Crafter Software Corporation. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.craftercms.studio.impl.v2.repository.blob;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.craftercms.commons.config.ConfigurationException;
import org.craftercms.commons.file.blob.Blob;
import org.craftercms.studio.api.v1.dal.DeploymentSyncHistory;
import org.craftercms.studio.api.v1.exception.ContentNotFoundException;
import org.craftercms.studio.api.v1.exception.ServiceLayerException;
import org.craftercms.studio.api.v1.exception.repository.*;
import org.craftercms.studio.api.v1.log.Logger;
import org.craftercms.studio.api.v1.log.LoggerFactory;
import org.craftercms.studio.api.v1.repository.ContentRepository;
import org.craftercms.studio.api.v1.repository.RepositoryItem;
import org.craftercms.studio.api.v1.service.deployment.DeploymentException;
import org.craftercms.studio.api.v1.service.deployment.DeploymentHistoryProvider;
import org.craftercms.studio.api.v1.to.DeploymentItemTO;
import org.craftercms.studio.api.v1.to.RemoteRepositoryInfoTO;
import org.craftercms.studio.api.v1.to.VersionTO;
import org.craftercms.studio.api.v1.util.filter.DmFilterWrapper;
import org.craftercms.studio.api.v2.dal.GitLog;
import org.craftercms.studio.api.v2.dal.PublishingHistoryItem;
import org.craftercms.studio.api.v2.dal.RepoOperation;
import org.craftercms.studio.api.v2.repository.blob.StudioBlobStore;
import org.craftercms.studio.api.v2.repository.blob.StudioBlobStoreResolver;
import org.craftercms.studio.impl.v1.repository.git.GitContentRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Implementation of {@link ContentRepository}, {@link org.craftercms.studio.api.v2.repository.ContentRepository} and
 * {@link DeploymentHistoryProvider} that delegates calls to a {@link StudioBlobStore} when appropriate
 *
 * @author joseross
 * @since 3.1.6
 */
public class BlobAwareContentRepository implements ContentRepository, DeploymentHistoryProvider,
        org.craftercms.studio.api.v2.repository.ContentRepository {

    private static final Logger logger = LoggerFactory.getLogger(BlobAwareContentRepository.class);

    /**
     * The extension for the blob files
     */
    protected String fileExtension;

    protected GitContentRepository localRepositoryV1;

    protected org.craftercms.studio.impl.v2.repository.GitContentRepository localRepositoryV2;

    protected StudioBlobStoreResolver blobStoreResolver;

    protected ObjectMapper objectMapper = new XmlMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public void setLocalRepositoryV1(GitContentRepository localRepositoryV1) {
        this.localRepositoryV1 = localRepositoryV1;
    }

    public void setLocalRepositoryV2(org.craftercms.studio.impl.v2.repository.GitContentRepository localRepositoryV2) {
        this.localRepositoryV2 = localRepositoryV2;
    }

    public void setBlobStoreResolver(StudioBlobStoreResolver blobStoreResolver) {
        this.blobStoreResolver = blobStoreResolver;
    }

    protected boolean isFolder(String path) {
        return isEmpty(FilenameUtils.getExtension(path));
    }

    protected String getOriginalPath(String path) {
        return StringUtils.removeEnd(path, "." + fileExtension);
    }

    protected String getPointerPath(String path) {
        return isFolder(path)? path : StringUtils.appendIfMissing(path, "." + fileExtension);
    }

    protected String normalize(String path) {
        return Paths.get(path).normalize().toString();
    }

    protected StudioBlobStore getBlobStore(String site, String... paths)
            throws ServiceLayerException, ConfigurationException, IOException {
        if (isEmpty(site)) {
            return null;
        }

        if (ArrayUtils.isEmpty(paths)) {
            throw new IllegalArgumentException("At least one path needs to be provided");
        }

        return (StudioBlobStore) blobStoreResolver.getByPaths(site, paths);
    }

    // Start API 1

    @Override
    public boolean contentExists(String site, String path) {
        logger.debug("Checking if {0} exists in site {1}", path, site);
        try {
            if (!isFolder(path)) {
                StudioBlobStore store = getBlobStore(site, path);
                if (store != null) {
                    return store.contentExists(site, normalize(path));
                }
            }
            return localRepositoryV1.contentExists(site, path);
        } catch (Exception e) {
            logger.error("Error checking if content {0} exist in site {1}", e, path, site);
            return false;
        }
    }

    @Override
    public InputStream getContent(String site, String path) {
        logger.debug("Getting content of {0} in site {1}", path, site);
        try {
            if (!isFolder(path)) {
                StudioBlobStore store = getBlobStore(site, path);
                if (store != null) {
                    return store.getContent(site, normalize(path));
                }
            }
            return localRepositoryV1.getContent(site, path);
        } catch (Exception e) {
            logger.error("Error getting content {0} in site {1}", e, path, site);
            return null;
        }
    }

    @Override
    public long getContentSize(String site, String path) {
        logger.debug("Getting size of {0} in site {1}", path, site);
        try {
            StudioBlobStore store = getBlobStore(site, path);
            if (store != null) {
                return store.getContentSize(site, normalize(path));
            }
            return localRepositoryV1.getContentSize(site, path);
        } catch (Exception e) {
            logger.error("Error getting size for content {0} in site {1}", e, path, site);
            return -1L;
        }
    }

    @Override
    public String writeContent(String site, String path, InputStream content) throws ServiceLayerException {
        logger.debug("Writing {0} in site {1}", path, site);
        try {
            StudioBlobStore store = getBlobStore(site, path);
            if (store != null) {
                store.writeContent(site, normalize(path), content);
                Blob reference = store.getReference(normalize(path));
                return localRepositoryV1.writeContent(site, getPointerPath(path),
                        new ByteArrayInputStream(objectMapper.writeValueAsBytes(reference)));
            }
            return localRepositoryV1.writeContent(site, path, content);
        } catch (Exception e) {
            logger.error("Error writing content {0} in site {1}", e, path, site);
            throw new ServiceLayerException(e);
        }
    }

    @Override
    public String createFolder(String site, String path, String name) {
        logger.debug("Creating folder {0} in site {1}", path, site);
        try {
            StudioBlobStore store = getBlobStore(site, path);
            if (store != null) {
                store.createFolder(site, normalize(path), name);
            }
            return localRepositoryV1.createFolder(site, path, name);
        } catch (Exception e) {
            logger.error("Error creating folder {0} in site {1}", e, path, site);
            return null;
        }
    }

    @Override
    public String deleteContent(String site, String path, String approver) {
        logger.debug("Deleting {0} in site {1}", path, site);
        try {
            StudioBlobStore store = getBlobStore(site, path);
            if (store != null) {
                String result = store.deleteContent(site, normalize(path), approver);
                if (result != null) {
                    return localRepositoryV1.deleteContent(site, getPointerPath(path), approver);
                }
            }
            return localRepositoryV1.deleteContent(site, path, approver);
        } catch (Exception e) {
            logger.error("Error deleting content {0} in site {1}", e, path, site);
            return null;
        }
    }

    @Override
    public Map<String, String> moveContent(String site, String fromPath, String toPath, String newName) {
        logger.debug("Moving content from {0} to {1} in site {2}", fromPath, toPath, site);
        try {
            StudioBlobStore store = getBlobStore(site, fromPath, toPath);
            if (store != null) {
                Map<String, String> result = store.moveContent(site, normalize(fromPath), normalize(toPath), newName);
                if (result != null) {
                    return localRepositoryV1.moveContent(site, getPointerPath(fromPath),
                            getPointerPath(toPath), newName);
                }
            }
            return localRepositoryV1.moveContent(site, fromPath, toPath, newName);
        } catch (Exception e) {
            logger.error("Error moving content from {0} to {1} in site {2}", e, fromPath, toPath, site);
            return null;
        }
    }

    @Override
    public String copyContent(String site, String fromPath, String toPath) {
        logger.debug("Copying content from {0} to {1} in site {2}", fromPath, toPath, site);
        try {
            StudioBlobStore store = getBlobStore(site, fromPath, toPath);
            if (store != null) {
                String result = store.copyContent(site, normalize(fromPath), normalize(toPath));
                if (result != null) {
                    return localRepositoryV1.copyContent(site, getPointerPath(fromPath), getPointerPath(toPath));
                }
            }
            return localRepositoryV1.copyContent(site, fromPath, toPath);
        } catch (Exception e) {
            logger.error("Error copying content from {0} to {1} in site {2}", e, fromPath, toPath, site);
            return null;
        }
    }

    @Override
    public RepositoryItem[] getContentChildren(String site, String path) {
        RepositoryItem[] children = localRepositoryV1.getContentChildren(site, path);
        return Stream.of(children)
                .peek(item -> item.name = getOriginalPath(item.name))
                .collect(toList())
                .toArray(new RepositoryItem[children.length]);
    }

    @Override
    public VersionTO[] getContentVersionHistory(String site, String path) {
        logger.debug("Getting version history for {0} in site {1}", path, site);
        try {
            StudioBlobStore store = getBlobStore(site, path);
            if (store != null) {
                return localRepositoryV1.getContentVersionHistory(site, getPointerPath(path));
            }
            return localRepositoryV1.getContentVersionHistory(site, path);
        } catch (Exception e) {
            logger.error("Error getting version history for {0} in site {1}", e, path, site);
            return null;
        }
    }

    @Override
    public String createVersion(String site, String path, boolean majorVersion) {
        return localRepositoryV1.createVersion(site, path, majorVersion);
    }

    @Override
    public String createVersion(String site, String path, String comment, boolean majorVersion) {
        return localRepositoryV1.createVersion(site, path, comment, majorVersion);
    }

    @Override
    public String revertContent(String site, String path, String version, boolean major, String comment) {
        return localRepositoryV1.revertContent(site, path, version, major, comment);
    }

    @Override
    public InputStream getContentVersion(String site, String path, String version) throws ContentNotFoundException {
        return localRepositoryV1.getContentVersion(site, path, version);
    }

    @Override
    public void lockItem(String site, String path) {
        localRepositoryV1.lockItem(site, path);
    }

    @Override
    public void lockItemForPublishing(String site, String path) {
        localRepositoryV1.lockItemForPublishing(site, path);
    }

    @Override
    public void unLockItem(String site, String path) {
        localRepositoryV1.unLockItem(site, path);
    }

    @Override
    public void unLockItemForPublishing(String site, String path) {
        localRepositoryV1.unLockItemForPublishing(site, path);
    }

    @Override
    public boolean isFolder(String siteId, String path) {
        return localRepositoryV1.isFolder(siteId, path);
    }

    @Override
    public List<DeploymentSyncHistory> getDeploymentHistory(String site, List<String> environmentNames,
                                                            ZonedDateTime fromDate, ZonedDateTime toDate,
                                                            DmFilterWrapper dmFilterWrapper, String filterType,
                                                            int numberOfItems) {
        List<DeploymentSyncHistory> histories = localRepositoryV1.getDeploymentHistory(site, environmentNames,
                fromDate, toDate, dmFilterWrapper, filterType, numberOfItems);

        return histories.stream()
                .peek(history -> history.setPath(getOriginalPath(history.getPath())))
                .collect(toList());
    }

    @Override
    public ZonedDateTime getLastDeploymentDate(String site, String path) {
        return localRepositoryV1.getLastDeploymentDate(site, path);
    }

    // TODO: Remove when the API is split

    @Override
    public boolean createSiteFromBlueprint(String blueprintLocation, String siteId, String sandboxBranch,
                                           Map<String, String> params) {
        return localRepositoryV1.createSiteFromBlueprint(blueprintLocation, siteId, sandboxBranch, params);
    }

    @Override
    public boolean deleteSite(String siteId) {
        return localRepositoryV1.deleteSite(siteId);
    }

    @Override
    public void initialPublish(String site, String sandboxBranch, String environment, String author, String comment)
            throws DeploymentException {
        localRepositoryV1.initialPublish(site, sandboxBranch, environment, author, comment);
    }

    @Override
    public void publish(String site, String sandboxBranch, List<DeploymentItemTO> deploymentItems, String environment,
                        String author, String comment) throws DeploymentException {
        logger.debug("Publishing items {0} to environment {1} in site {2}", deploymentItems, environment, site);
        Map<String, StudioBlobStore> stores = new LinkedHashMap<>();
        MultiValueMap<String, DeploymentItemTO> items = new LinkedMultiValueMap<>();
        List<DeploymentItemTO> localItems = new LinkedList<>();
        try {
            for (DeploymentItemTO item : deploymentItems) {
                StudioBlobStore store = getBlobStore(site, item.getPath());
                if (store != null) {
                    stores.putIfAbsent(store.getId(), store);
                    items.add(store.getId(), item);
                    localItems.add(mapDeploymentItem(item));
                } else {
                    localItems.add(item);
                }
            }
            for (String storeId : stores.keySet()) {
                logger.debug("Publishing blobs to environment {0} using store {1} for site {2}",
                        environment, storeId, site);
                stores.get(storeId).publish(site, sandboxBranch, items.get(storeId), environment, author, comment);
            }
            logger.debug("Publishing local files to environment {0} for site {1}", environment, site);
            localRepositoryV1.publish(site, sandboxBranch, localItems, environment, author, comment);
        } catch (Exception e) {
            throw new DeploymentException("Error during deployment to environment " +
                    environment + " for site " + site, e);
        }
    }

    protected DeploymentItemTO mapDeploymentItem(DeploymentItemTO item) {
        DeploymentItemTO pointer = new DeploymentItemTO();
        pointer.setPath(getPointerPath(item.getPath()));
        pointer.setSite(item.getSite());
        pointer.setCommitId(item.getCommitId());
        pointer.setMove(item.isMove());
        pointer.setDelete(item.isDelete());
        pointer.setOldPath(getPointerPath(item.getOldPath()));
        pointer.setPackageId(item.getPackageId());
        return pointer;
    }

    @Override
    public String getRepoLastCommitId(String site) {
        return localRepositoryV1.getRepoLastCommitId(site);
    }

    @Override
    public String getRepoFirstCommitId(String site) {
        return localRepositoryV1.getRepoFirstCommitId(site);
    }

    @Override
    public List<String> getEditCommitIds(String site, String path, String commitIdFrom, String commitIdTo) {
        return localRepositoryV1.getEditCommitIds(site, path, commitIdFrom, commitIdTo);
    }

    @Override
    public boolean commitIdExists(String site, String commitId) {
        return localRepositoryV1.commitIdExists(site, commitId);
    }

    @Override
    public void insertFullGitLog(String siteId, int processed) {
        localRepositoryV1.insertFullGitLog(siteId, processed);
    }

    @Override
    public void deleteGitLogForSite(String siteId) {
        localRepositoryV1.deleteGitLogForSite(siteId);
    }

    @Override
    public boolean createSiteCloneRemote(String siteId, String sandboxBranch, String remoteName, String remoteUrl,
                                         String remoteBranch, boolean singleBranch, String authenticationType,
                                         String remoteUsername, String remotePassword, String remoteToken,
                                         String remotePrivateKey, Map<String, String> params, boolean createAsOrphan)
            throws InvalidRemoteRepositoryException, InvalidRemoteRepositoryCredentialsException,
            RemoteRepositoryNotFoundException, ServiceLayerException {
        return localRepositoryV1.createSiteCloneRemote(siteId, sandboxBranch, remoteName, remoteUrl, remoteBranch,
                singleBranch, authenticationType, remoteUsername, remotePassword, remoteToken, remotePrivateKey,
                params, createAsOrphan);
    }

    @Override
    public boolean createSitePushToRemote(String siteId, String remoteName, String remoteUrl,
                                          String authenticationType, String remoteUsername, String remotePassword,
                                          String remoteToken, String remotePrivateKey, boolean createAsOrphan)
            throws InvalidRemoteRepositoryException, InvalidRemoteRepositoryCredentialsException,
            RemoteRepositoryNotFoundException, RemoteRepositoryNotBareException, ServiceLayerException {
        return localRepositoryV1.createSitePushToRemote(siteId, remoteName, remoteUrl, authenticationType,
                remoteUsername, remotePassword, remoteToken, remotePrivateKey, createAsOrphan);
    }

    @Override
    public boolean addRemote(String siteId, String remoteName, String remoteUrl, String authenticationType,
                             String remoteUsername, String remotePassword, String remoteToken, String remotePrivateKey)
            throws InvalidRemoteUrlException, ServiceLayerException {
        return localRepositoryV1.addRemote(siteId, remoteName, remoteUrl, authenticationType, remoteUsername,
                remotePassword, remoteToken, remotePrivateKey);
    }

    @Override
    public boolean removeRemote(String siteId, String remoteName) {
        return localRepositoryV1.removeRemote(siteId, remoteName);
    }

    @Override
    public void removeRemoteRepositoriesForSite(String siteId) {
        localRepositoryV1.removeRemoteRepositoriesForSite(siteId);
    }

    @Override
    public List<RemoteRepositoryInfoTO> listRemote(String siteId, String sandboxBranch) throws ServiceLayerException {
        return localRepositoryV1.listRemote(siteId, sandboxBranch);
    }

    @Override
    public boolean pushToRemote(String siteId, String remoteName, String remoteBranch) throws ServiceLayerException,
            InvalidRemoteUrlException {
        return localRepositoryV1.pushToRemote(siteId, remoteName, remoteBranch);
    }

    @Override
    public boolean pullFromRemote(String siteId, String remoteName, String remoteBranch) throws ServiceLayerException,
            InvalidRemoteUrlException {
        return localRepositoryV1.pullFromRemote(siteId, remoteName, remoteBranch);
    }

    @Override
    public void resetStagingRepository(String siteId) throws ServiceLayerException {
        localRepositoryV1.resetStagingRepository(siteId);
    }

    @Override
    public void reloadRepository(String siteId) {
        localRepositoryV1.reloadRepository(siteId);
    }

    @Override
    public void cleanupRepositories(String siteId) {
        localRepositoryV1.cleanupRepositories(siteId);
    }

    @Override
    public boolean repositoryExists(String site) {
        return localRepositoryV1.repositoryExists(site);
    }

    // Start API 2

    @Override
    public GitLog getGitLog(String siteId, String commitId) {
        return localRepositoryV2.getGitLog(siteId, commitId);
    }

    @Override
    public void markGitLogVerifiedProcessed(String siteId, String commitId) {
        localRepositoryV2.markGitLogVerifiedProcessed(siteId, commitId);
    }

    @Override
    public void insertGitLog(String siteId, String commitId, int processed) {
        localRepositoryV2.insertGitLog(siteId, commitId, processed);
    }

    @Override
    public List<String> getSubtreeItems(String site, String path) {
        return localRepositoryV2.getSubtreeItems(site, path).stream()
                .map(this::getOriginalPath)
                .collect(toList());
    }

    @Override
    public List<RepoOperation> getOperations(String site, String commitIdFrom, String commitIdTo) {
        return localRepositoryV2.getOperations(site, commitIdFrom, commitIdTo).stream()
                .peek(operation -> {
                    operation.setPath(getOriginalPath(operation.getPath()));
                    operation.setMoveToPath(getOriginalPath(operation.getMoveToPath()));
                })
                .collect(toList());
    }

    @Override
    public List<RepoOperation> getOperationsFromDelta(String site, String commitIdFrom, String commitIdTo) {
        return localRepositoryV2.getOperationsFromDelta(site, commitIdFrom, commitIdTo).stream()
                .peek(operation -> {
                    operation.setPath(getOriginalPath(operation.getPath()));
                    operation.setMoveToPath(getOriginalPath(operation.getMoveToPath()));
                })
                .collect(toList());
    }

    @Override
    public List<PublishingHistoryItem> getPublishingHistory(String siteId, String environment, String path,
                                                            String publisher, ZonedDateTime fromDate,
                                                            ZonedDateTime toDate, int limit) {
        return localRepositoryV2.getPublishingHistory(siteId, environment, path, publisher, fromDate, toDate, limit);
    }
}
