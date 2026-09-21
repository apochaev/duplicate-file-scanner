package us.pochaev.dfs.scanner.domain.usecases.command;

import us.pochaev.dfs.scanner.domain.model.ScanId;

public interface CancelScan {

    void cancel(ScanId scanId);
}
