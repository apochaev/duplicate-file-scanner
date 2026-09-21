package us.pochaev.dfs.scanner.domain.usecases.query;

import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;

public interface GetScanStatus {

    ScanStatus status(ScanId scanId);
}
