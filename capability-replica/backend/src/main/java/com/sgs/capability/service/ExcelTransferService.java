package com.sgs.capability.service;

import com.sgs.capability.dto.*;
import com.sgs.capability.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;

/** Excel reader/writer for the copied import and export flow. */
@Service
public class ExcelTransferService {
    public static final String EXCEL_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final List<Column<SubcontractAbility>> SUBCONTRACT_COLUMNS = List.of(
            column("实验室名称", item -> item.labName),
            column("联系方式", item -> item.contactDetails),
            column("检测/校准项目或类别", item -> item.testCategory),
            column("CMA/CNAS No(截止日期)", item -> item.cmaOrCnas),
            column("选定依据", item -> item.gist),
            column("评估人", item -> item.appraiser),
            column("评估结果", item -> item.evaluationResult)
    );

    private static final List<TemplateColumn> ORIGINAL_TEMPLATE_COLUMNS = List.of(
            new TemplateColumn("samplingName", "样品名称", "Sample Name"),
            new TemplateColumn("testItem", "测试项目", "Testing Item"),
            new TemplateColumn("price", "价格/CNY", "Price/CNY"),
            new TemplateColumn("methodName", "方法中文描述", "Method Description in Chinese"),
            new TemplateColumn("standardNo", "标准编号", "Standard Number"),
            new TemplateColumn("cycleWorkingDay", "检测周期/工作日", "TAT/Working Day"),
            new TemplateColumn("remark", "备注", "Remark"),
            new TemplateColumn("massRequired", "所需样品量", "Required Sample Weight/g"),
            new TemplateColumn("sizeRequired", "样品粒度要求", "Size Requirement/mm"),
            new TemplateColumn("methodEngName", "方法英文描述", "Method Description in English"),
            new TemplateColumn("detectionLimit", "适用范围", "Application Scope")
    );

    private static final List<Column<ImportAbilityTableDto>> ABILITY_IMPORT_ERROR_COLUMNS = List.of(
            column("行号", item -> item.rowNumber <= 0 ? "" : String.valueOf(item.rowNumber)),
            column("业务线", item -> item.orgName),
            column("类型", item -> item.typeName),
            column("样品名称", item -> item.samplingName),
            column("产品代码", item -> item.productCode),
            column("测试项目", item -> item.testItem),
            column("标准号", item -> item.standardNo),
            column("方法中文描述", item -> item.methodName),
            column("方法英文描述", item -> item.methodEngName),
            column("检测周期/工作日", item -> item.cycleWorkingDay),
            column("样品量(g)", item -> item.massRequired),
            column("粒度要求", item -> item.sizeRequired),
            column("适用范围", item -> item.detectionLimit),
            column("价格", item -> item.price),
            column("异常", item -> item.exception)
    );

    private static final List<Column<SubcontractAbility>> SUBCONTRACT_ERROR_COLUMNS = List.of(
            column("实验室名称", item -> item.labName),
            column("联系方式", item -> item.contactDetails),
            column("检测/校准项目或类别", item -> item.testCategory),
            column("CMA/CNAS No(截止日期)", item -> item.cmaOrCnas),
            column("选定依据", item -> item.gist),
            column("评估人", item -> item.appraiser),
            column("评估结果", item -> item.evaluationResult),
            column("异常", item -> item.exception)
    );

    private static final List<Column<UpdateStandardNumberDto>> STANDARD_ERROR_COLUMNS = List.of(
            column("原标准号", item -> item.old),
            column("新标准号", item -> item.newValue),
            column("标准名称", item -> item.name),
            column("标准状态", item -> item.statu),
            column("备注", item -> item.remark),
            column("命中数量", item -> String.valueOf(item.matchedCount)),
            column("更新数量", item -> String.valueOf(item.updatedCount)),
            column("异常", item -> item.exception)
    );

    private static final List<Column<AuditLog>> AUDIT_LOG_COLUMNS = List.of(
            column("Time", item -> item.executionTime),
            column("User name", item -> item.userName),
            column("Service", item -> item.serviceName),
            column("Action", item -> item.methodName),
            column("Parameters", item -> item.parameters),
            column("Duration", item -> item.executionDuration == null ? "" : String.valueOf(item.executionDuration)),
            column("IP address", item -> item.clientIpAddress),
            column("Client", item -> item.clientName),
            column("Browser", item -> item.browserInfo),
            column("Error state", item -> safe(item.exception).isBlank() ? "Success" : item.exception)
    );

    private static final List<Column<EntityChangeItem>> ENTITY_CHANGE_COLUMNS = List.of(
            column("Action", item -> item.changeTypeName),
            column("Object", item -> item.entityTypeFullName),
            column("User name", item -> item.userName),
            column("Time", item -> item.changeTime)
    );

    private static final List<Column<UserItem>> USER_COLUMNS = List.of(
            column("Name", item -> item.name),
            column("Surname", item -> item.surname),
            column("User name", item -> item.userName),
            column("Phone number", item -> item.phoneNumber),
            column("Email address", item -> item.emailAddress),
            column("Email confirm", item -> boolText(item.isEmailConfirmed)),
            column("Roles", item -> joinValues(item.assignedRoleNames, ", ")),
            column("Active", item -> boolText(item.isActive)),
            column("Creation time", item -> item.creationTime == null ? "" : item.creationTime.toString())
    );

    private static final List<Column<ChatExportRow>> CHAT_COLUMNS = List.of(
            column("From", item -> item.from),
            column("To", item -> item.to),
            column("Message", item -> item.message),
            column("Read state", item -> item.readState),
            column("Creation time", item -> item.creationTime)
    );

    private static final List<Column<ImportUserDto>> USER_IMPORT_ERROR_COLUMNS = List.of(
            column("UserName", item -> item.userName),
            column("Name", item -> item.name),
            column("Surname", item -> item.surname),
            column("EmailAddress", item -> item.emailAddress),
            column("PhoneNumber", item -> item.phoneNumber),
            column("Password", item -> item.password),
            column("Roles", item -> joinValues(item.assignedRoleNames)),
            column("Refuse Reason", item -> item.exception)
    );

    private static final Set<String> POSITIVE_LAB_VALUES = Set.of("1", "TRUE", "YES", "Y", "是", "有", "有能力", "ALL",
            "√", "✓", "CMA", "CNAS", "CMA/CNAS", "CNAS/CMA", "CMACNAS", "CNASCMA");
    private static final Set<String> NEGATIVE_LAB_VALUES = Set.of("0", "FALSE", "NO", "N", "否", "无", "无能力",
            "×", "X", "N/A", "NA", "不适用");

    private final CapabilityStore store;
    private final TempFileService tempFiles;
    private final Path historyPath;

    public ExcelTransferService(CapabilityStore store, TempFileService tempFiles,
                                @Value("${replica.history.path:data/history}") String historyPath) {
        this.store = store;
        this.tempFiles = tempFiles;
        this.historyPath = Path.of(historyPath);
    }

    public FileDto abilityTemplate() {
        return abilityTemplate(null);
    }

    public FileDto abilityTemplate(Long orgId) {
        List<TemplateColumn> columns = ORIGINAL_TEMPLATE_COLUMNS;
        String fileName = "能力表模板.xlsx";
        if (orgId != null) {
            columns = templateColumns(store.orgSetting(orgId).propertyName);
            fileName = store.orgUnits().stream()
                    .filter(org -> Objects.equals(org.id, orgId))
                    .findFirst()
                    .map(org -> org.displayName + "导入模板" + windowsFileTimeNow() + ".xlsx")
                    .orElse(fileName);
        }
        return tempFiles.put(fileName, EXCEL_TYPE, originalAbilityTemplateBytes(columns));
    }

    public FileDto abilityExport() {
        List<Ability> rows = store.allAbilities();
        String orgName = exportOrgName("", rows);
        return tempFiles.put(abilityExportFileName(orgName), EXCEL_TYPE,
                abilityWorkbookBytes(abilityExportSheetName(orgName), rows, orgName));
    }

    public FileDto abilityExport(FindAbilityRequest input) {
        List<Ability> rows = input == null ? store.allAbilities() : store.findAllAbilities(input);
        String orgName = exportOrgName(input, rows);
        return tempFiles.put(abilityExportFileName(orgName), EXCEL_TYPE,
                abilityWorkbookBytes(abilityExportSheetName(orgName), rows, orgName));
    }

    private String exportOrgName(FindAbilityRequest input, List<Ability> rows) {
        if (input != null && input.orgId != null) {
            return store.orgUnits().stream()
                    .filter(org -> Objects.equals(org.id, input.orgId))
                    .findFirst()
                    .map(org -> safe(org.displayName))
                    .orElseGet(() -> exportOrgName("", rows));
        }
        return exportOrgName("", rows);
    }

    private String abilityExportFileName(String orgName) {
        return safe(orgName).isBlank()
                ? "能力表导出.xlsx"
                : safe(orgName) + "能力表数据--" + windowsFileTimeNow() + ".xlsx";
    }

    private long windowsFileTimeNow() {
        Instant now = Instant.now();
        return 116_444_736_000_000_000L + now.getEpochSecond() * 10_000_000L + now.getNano() / 100L;
    }

    private String abilityExportSheetName(String orgName) {
        String sheetName = safe(orgName).isBlank() ? "能力表" : safe(orgName) + "能力表数据";
        return WorkbookUtil.createSafeSheetName(sheetName);
    }

    public FileDto auditLogExport(List<AuditLog> rows) {
        return tempFiles.put("AuditLogs.xlsx", EXCEL_TYPE, auditLogWorkbookBytes(rows));
    }

    public FileDto entityChangesExport(List<EntityChangeItem> rows) {
        return tempFiles.put("DetailedLogs.xlsx", EXCEL_TYPE, entityChangeWorkbookBytes(rows));
    }

    public FileDto userExport(List<UserItem> rows) {
        return tempFiles.put("UserList.xlsx", EXCEL_TYPE, userWorkbookBytes(rows));
    }

    public FileDto chatMessagesExport(Long userId, Long targetUserId, Integer targetTenantId, List<ChatMessageItem> rows) {
        boolean hasMessages = rows != null && !rows.isEmpty();
        // GDPR provider writes "." for host conversations; exporter uses Anonymous only for empty message sets.
        String targetTenantName = hasMessages
                ? targetTenantName(targetTenantId)
                : "Anonymous";
        String targetUserName = hasMessages
                ? store.user(targetUserId).map(user -> safe(user.userName)).orElse("Anonymous")
                : "Anonymous";
        return tempFiles.put("Chat_" + targetTenantName + "_" + targetUserName + ".xlsx", EXCEL_TYPE,
                chatWorkbookBytes(targetTenantName, targetUserName, rows));
    }

    public UserImportOutput importUsers(FileDto file) {
        return importUsers(file, null);
    }

    public UserImportOutput importUsers(FileDto file, Long notificationUserId) {
        UserImportOutput output = parseUserImport(file);
        boolean invalidFile = output.invalidFile || output.items.isEmpty();
        output.invalidFile = invalidFile;
        for (ImportUserDto item : output.items) {
            if (!safe(item.exception).isBlank()) {
                continue;
            }
            UserItem user = new UserItem();
            user.userName = item.userName;
            user.name = item.name;
            user.surname = item.surname;
            user.emailAddress = item.emailAddress;
            user.phoneNumber = item.phoneNumber;
            user.isActive = true;
            user.isEmailConfirmed = true;
            user.isLockoutEnabled = true;
            user.preferredLanguageName = "zh-Hans";
            store.saveImportedUser(user, item.assignedRoleNames, item.password);
            output.importedCount++;
        }
        summarizeUserImport(output);
        if (notificationUserId != null) {
            if (invalidFile) {
                store.notifyUserImportFileInvalid(notificationUserId);
            } else if (output.errorFile != null) {
                store.notifyInvalidUserImport(notificationUserId, output.errorFile);
            } else {
                store.notifyUserImportSucceeded(notificationUserId);
            }
        }
        return output;
    }

    public FileDto subcontractTemplate() {
        List<SubcontractAbility> sample = store.subcontractAbilities(null).stream().limit(1).toList();
        return tempFiles.put("分包能力模板.xlsx", EXCEL_TYPE, workbookBytes("分包能力", sample, false));
    }

    public FileDto storeUpload(MultipartFile file) throws IOException {
        return tempFiles.put(file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }

    public AbilityTableUploadOutput parseAbilityUpload(FileDto file, Long orgId) {
        AbilityTableUploadOutput output = new AbilityTableUploadOutput();
        output.file = file;
        Set<String> knownLabCodes = knownLabCodes();
        try (Workbook workbook = open(file)) {
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    continue;
                }
                Map<String, Integer> headers = headers(headerRow);
                Map<Integer, String> labColumns = labColumns(sheet, headers);
                int startRow = headers.containsKey("实验室能力") ? 2 : 1;
                labColumns.values().stream().filter(value -> !output.labCodeList.contains(value)).forEach(output.labCodeList::add);
                for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null || rowIsBlank(row)) {
                        continue;
                    }
                    ImportAbilityTableDto item = readAbilityRow(row, headers, orgId, sheet.getSheetName(), labColumns, knownLabCodes);
                    item.rowNumber = rowIndex + 1;
                    item.labData.keySet().stream()
                            .filter(value -> !output.labCodeList.contains(value))
                            .forEach(output.labCodeList::add);
                    if (safe(item.exception).isBlank()) {
                        store.findDuplicateAbility(toAbility(item, orgId)).ifPresent(existing -> {
                            item.isExist = true;
                            item.existId = existing.id;
                        });
                    }
                    output.abilityTableList.add(item);
                }
            }
        } catch (RuntimeException | IOException ex) {
            ImportAbilityTableDto failed = new ImportAbilityTableDto();
            failed.exception = ex.getMessage();
            output.abilityTableList.add(failed);
        }
        summarizeAbilityUpload(output);
        return output;
    }

    public String abilityUploadContextError(Long orgId) {
        if (orgId == null || !store.hasOrgAbilitySetting(orgId)) {
            return "没有设置业务部门能力信息";
        }
        if (!store.hasOrganizationUnit(orgId)) {
            return "导入的部门不存在";
        }
        return null;
    }

    public UploadSubcontractAbilityOutput parseSubcontractUpload(FileDto file) {
        UploadSubcontractAbilityOutput output = new UploadSubcontractAbilityOutput();
        output.file = file;
        try (Workbook workbook = open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            // Original NPOI import uses header row 1 and data start row 2.
            int headerIndex = 1;
            Map<String, Integer> headers = headers(sheet.getRow(headerIndex));
            for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || rowIsBlank(row)) {
                    continue;
                }
                SubcontractAbility item = readSubcontractRow(row, headers);
                if (safe(item.exception).isBlank()) {
                    store.findDuplicateSubcontractAbility(item).ifPresent(existing -> {
                        item.isExist = true;
                        item.existId = existing.id;
                    });
                }
                output.items.add(item);
            }
        } catch (RuntimeException | IOException ex) {
            SubcontractAbility failed = new SubcontractAbility();
            failed.exception = ex.getMessage();
            output.items.add(failed);
        }
        summarizeSubcontractUpload(output);
        return output;
    }

    public UploadStandardOutput parseStandardUpload(FileDto file) {
        UploadStandardOutput output = new UploadStandardOutput();
        output.file = file;
        try (Workbook workbook = open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headers = headers(sheet.getRow(0));
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || rowIsBlank(row)) {
                    continue;
                }
                output.items.add(readStandardRow(row, headers));
            }
        } catch (RuntimeException | IOException ex) {
            UpdateStandardNumberDto failed = new UpdateStandardNumberDto();
            failed.exception = ex.getMessage();
            output.items.add(failed);
        }
        summarizeStandardUpload(output);
        return output;
    }

    private UserImportOutput parseUserImport(FileDto file) {
        UserImportOutput output = new UserImportOutput();
        output.file = file;
        try (Workbook workbook = open(file)) {
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null || userImportRowIsBlank(row)) {
                        continue;
                    }
                    output.items.add(readUserImportRow(row));
                }
            }
        } catch (RuntimeException | IOException ex) {
            output.invalidFile = true;
        }
        summarizeUserImport(output);
        return output;
    }

    public void saveAbilityRows(SaveAbilityExcelInput input) {
        saveAbilityRows(input, 1L);
    }

    public void saveAbilityRows(SaveAbilityExcelInput input, Long actorUserId) {
        List<ImportAbilityTableDto> rows = input.dataList;
        if ((rows == null || rows.isEmpty()) && input.file != null) {
            rows = parseAbilityUpload(input.file, null).abilityTableList;
        }
        if (rows == null) {
            return;
        }
        for (ImportAbilityTableDto row : rows) {
            if (row.exception != null && !row.exception.isBlank()) {
                continue;
            }
            String labError = validateLabData(row.labData, knownLabCodes());
            if (!labError.isBlank()) {
                row.exception = labError;
                continue;
            }
            Ability ability = toAbility(row, null);
            if (row.existId != null) {
                if (input.onlySaveNew) {
                    continue;
                }
                ability.id = row.existId;
            }
            store.saveAbility(ability, actorUserId);
        }
    }

    public void saveSubcontractRows(SaveSubcontractAbilityExcelInput input) {
        List<SubcontractAbility> rows = input.dataList;
        if ((rows == null || rows.isEmpty()) && input.file != null) {
            rows = parseSubcontractUpload(input.file).items;
        }
        if (rows != null) {
            store.saveSubcontractAbilities(rows, input.onlySaveNew);
        }
    }

    public UploadStandardOutput applyStandardUpdate(UploadStandardOutput input) {
        return applyStandardUpdate(input, "Admin");
    }

    public UploadStandardOutput applyStandardUpdate(UploadStandardOutput input, String currentUserName) {
        return applyStandardUpdate(input, currentUserName, 1L);
    }

    public UploadStandardOutput applyStandardUpdate(UploadStandardOutput input, String currentUserName, Long actorUserId) {
        UploadStandardOutput output = input == null ? new UploadStandardOutput() : input;
        if ((output.items == null || output.items.isEmpty()) && output.file != null) {
            output.items = parseStandardUpload(output.file).items;
        }
        copyStandardSourceToHistory(output.file, currentUserName);
        if (output.items == null) {
            return output;
        }
        for (UpdateStandardNumberDto item : output.items) {
            if (item == null) {
                continue;
            }
            item.matchedCount = store.countStandardMatches(item.old);
            item.updatedCount = store.updateStandardNumber(item.old, item.newValue, currentUserName, actorUserId);
        }
        summarizeStandardUpload(output);
        return output;
    }

    private void copyStandardSourceToHistory(FileDto file, String currentUserName) {
        if (file == null || safe(file.fileToken).isBlank()) {
            return;
        }
        try {
            // Original StandardAppService copies the uploaded temp file before applying updates.
            Files.createDirectories(historyPath);
            Files.write(historyPath.resolve(standardHistoryFileName(currentUserName, file.fileName)),
                    tempFiles.requireContent(file));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to copy standard update source file to history", ex);
        }
    }

    private String standardHistoryFileName(String currentUserName, String fileName) {
        String userName = safe(currentUserName).isBlank() ? "Admin" : safe(currentUserName);
        return userName + "$" + windowsFileTimeNow() + "$" + safe(fileName);
    }

    private byte[] workbookBytes(String sheetName, List<?> rows, boolean abilitySheet) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            if (abilitySheet) {
                writeAbilitySheet(sheet, castRows(rows, Ability.class), "");
            } else {
                writeSubcontractSheet(sheet, castRows(rows, SubcontractAbility.class));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create Excel file", ex);
        }
    }

    private byte[] abilityWorkbookBytes(String sheetName, List<Ability> rows, String orgName) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            writeAbilitySheet(sheet, rows, orgName);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create Excel file", ex);
        }
    }

    private <T> byte[] workbookBytes(String sheetName, List<T> rows, List<Column<T>> columns) {
        return workbookBytes(sheetName, rows, columns, 1);
    }

    private <T> byte[] workbookBytes(String sheetName, List<T> rows, List<Column<T>> columns, int dataStartRow) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            writeGenericSheet(sheet, rows, columns, dataStartRow);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create Excel file", ex);
        }
    }

    private byte[] auditLogWorkbookBytes(List<AuditLog> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("AuditLogs");
            // Original NPOI export leaves the second row blank and starts data on row index 2.
            writeGenericSheet(sheet, rows == null ? List.of() : rows, AUDIT_LOG_COLUMNS, 2, Set.of(4, 9));
            applyDateTimeFormat(sheet, 2, rows == null ? 0 : rows.size(), 0);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create audit log Excel file", ex);
        }
    }

    private byte[] entityChangeWorkbookBytes(List<EntityChangeItem> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("DetailedLogs");
            // Original NPOI export leaves the second row blank and starts data on row index 2.
            writeGenericSheet(sheet, rows == null ? List.of() : rows, ENTITY_CHANGE_COLUMNS, 2);
            applyDateTimeFormat(sheet, 2, rows == null ? 0 : rows.size(), 3);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create entity change Excel file", ex);
        }
    }

    private byte[] userWorkbookBytes(List<UserItem> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Users");
            writeGenericSheet(sheet, rows == null ? List.of() : rows, USER_COLUMNS, 2);
            applyDateFormat(sheet, 2, rows == null ? 0 : rows.size(), 8);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create user Excel file", ex);
        }
    }

    private byte[] chatWorkbookBytes(String targetTenantName, String targetUserName, List<ChatMessageItem> rows) {
        List<ChatExportRow> exportRows = (rows == null ? List.<ChatMessageItem>of() : rows).stream()
                .map(item -> chatExportRow(targetTenantName, targetUserName, item))
                .toList();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Messages");
            writeGenericSheet(sheet, exportRows, CHAT_COLUMNS, 2);
            applyDateTimeFormat(sheet, 2, exportRows.size(), 4);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create chat message Excel file", ex);
        }
    }

    private ChatExportRow chatExportRow(String targetTenantName, String targetUserName, ChatMessageItem item) {
        String target = targetTenantName + "/" + targetUserName;
        boolean receiver = item.side == 2;
        return new ChatExportRow(
                receiver ? target : "You",
                receiver ? "You" : target,
                item.message,
                readStateName(receiver ? item.readState : item.receiverReadState),
                item.creationTime
        );
    }

    private String targetTenantName(Integer targetTenantId) {
        if (targetTenantId == null) {
            return ".";
        }
        return store.tenant(targetTenantId)
                .map(tenant -> safe(tenant.tenancyName))
                .filter(value -> !value.isBlank())
                .orElse("Tenant-" + targetTenantId);
    }

    private <T> void writeGenericSheet(Sheet sheet, List<T> rows, List<Column<T>> columns) {
        writeGenericSheet(sheet, rows, columns, 1);
    }

    private <T> void writeGenericSheet(Sheet sheet, List<T> rows, List<Column<T>> columns, int dataStartRow) {
        writeGenericSheet(sheet, rows, columns, dataStartRow, Set.of());
    }

    private <T> void writeGenericSheet(Sheet sheet, List<T> rows, List<Column<T>> columns, int dataStartRow,
                                       Set<Integer> autosizeExcludedColumns) {
        Row header = sheet.createRow(0);
        CellStyle headerStyle = headerStyle(sheet.getWorkbook());
        for (int index = 0; index < columns.size(); index++) {
            writeHeaderCell(header, index, columns.get(index).title, headerStyle);
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + dataStartRow);
            T item = rows.get(rowIndex);
            for (int index = 0; index < columns.size(); index++) {
                row.createCell(index).setCellValue(safe(columns.get(index).reader.read(item)));
            }
        }
        autosize(sheet, columns.size(), autosizeExcludedColumns);
    }

    private void applyDateTimeFormat(Sheet sheet, int firstRow, int rowCount, int columnIndex) {
        applyDateFormat(sheet, firstRow, rowCount, columnIndex, "yyyy-mm-dd hh:mm:ss");
    }

    private void applyDateFormat(Sheet sheet, int firstRow, int rowCount, int columnIndex) {
        applyDateFormat(sheet, firstRow, rowCount, columnIndex, "yyyy-mm-dd");
    }

    private void applyDateFormat(Sheet sheet, int firstRow, int rowCount, int columnIndex, String dataFormat) {
        CellStyle style = dateStyle(sheet.getWorkbook(), dataFormat);
        for (int offset = 0; offset < rowCount; offset++) {
            Row row = sheet.getRow(firstRow + offset);
            if (row == null) {
                continue;
            }
            Cell cell = row.getCell(columnIndex);
            if (cell == null) {
                continue;
            }
            String value = cell.getStringCellValue();
            cell.setCellStyle(style);
            parseDateTime(value).ifPresent(cell::setCellValue);
        }
    }

    private byte[] originalAbilityTemplateBytes(List<TemplateColumn> columns) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("sheet1");
            Row header = sheet.createRow(0);
            Row englishHeader = sheet.createRow(1);
            CellStyle headerStyle = headerStyle(workbook);
            int columnIndex = 0;
            for (TemplateColumn column : columns) {
                writeHeaderCell(header, columnIndex, column.title, headerStyle);
                writeHeaderCell(englishHeader, columnIndex, column.englishTitle, headerStyle);
                columnIndex++;
            }
            int labStart = columnIndex;
            writeHeaderCell(header, labStart, "实验室能力", headerStyle);
            for (Laboratory lab : store.labs()) {
                writeHeaderCell(englishHeader, columnIndex++, lab.code, headerStyle);
            }
            if (columnIndex > labStart + 1) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, labStart, columnIndex - 1));
            }
            addLabValueValidation(sheet, labStart, Math.max(labStart, columnIndex - 1));
            autosize(sheet, Math.max(columnIndex, labStart + 1));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create ability template", ex);
        }
    }

    private List<TemplateColumn> templateColumns(Collection<String> propertyNames) {
        if (propertyNames == null || propertyNames.isEmpty()) {
            return ORIGINAL_TEMPLATE_COLUMNS;
        }
        Set<String> enabled = new LinkedHashSet<>(propertyNames.stream().map(this::camelCaseProperty).toList());
        return ORIGINAL_TEMPLATE_COLUMNS.stream()
                .filter(column -> enabled.contains(column.property))
                .toList();
    }

    private String camelCaseProperty(String propertyName) {
        String value = safe(propertyName);
        return value.isBlank() ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private void addLabValueValidation(Sheet sheet, int firstColumn, int lastColumn) {
        if (!(sheet instanceof XSSFSheet xssfSheet)) {
            return;
        }
        // The original template exposes the same four dropdown values for lab ability cells.
        XSSFDataValidationHelper helper = new XSSFDataValidationHelper(xssfSheet);
        DataValidation validation = helper.createValidation(
                helper.createExplicitListConstraint(new String[]{"ALL", "CNAS", "CMA", "√"}),
                new CellRangeAddressList(2, 65535, firstColumn, lastColumn)
        );
        validation.createErrorBox("错误", "请按右侧下拉箭头选择!");
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private void writeAbilitySheet(Sheet sheet, List<Ability> rows, String orgName) {
        List<Laboratory> labs = exportedLabs(rows);
        List<AbilityExportColumn> columns = abilityExportColumns(exportOrgName(orgName, rows), labs);
        Row header = sheet.createRow(0);
        Row subHeader = sheet.createRow(1);
        CellStyle headerStyle = headerStyle(sheet.getWorkbook());
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            AbilityExportColumn column = columns.get(columnIndex);
            writeHeaderCell(header, columnIndex, column.title, headerStyle);
            writeHeaderCell(subHeader, columnIndex, column.subtitle, headerStyle);
        }

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Ability item = rows.get(rowIndex);
            Row row = sheet.createRow(rowIndex + 2);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                row.createCell(columnIndex).setCellValue(safe(columns.get(columnIndex).reader.read(item)));
            }
        }
        // Original AbilityDataExporter leaves column widths at the XSSF defaults.
    }

    private List<AbilityExportColumn> abilityExportColumns(String orgName, List<Laboratory> labs) {
        List<AbilityExportColumn> columns = new ArrayList<>();
        columns.add(abilityExportColumn("样品名称", "Sample Name", item -> item.samplingName));
        columns.add(abilityExportColumn("测试项目", "Standard Number", item -> item.testItem));
        columns.add(abilityExportColumn("价格/CNY", "Price/CNY", item -> item.price));
        if (isExportOrg(orgName, "CHEM")) {
            columns.add(abilityExportColumn("备注", "Remark", item -> item.remark));
        }
        columns.add(abilityExportColumn("标准编号", "Standard Number", item -> item.standardNo));
        if (isExportOrg(orgName, "NF")) {
            columns.add(abilityExportColumn("备注", "Remark", item -> item.remark));
        }
        columns.add(abilityExportColumn("方法中文描述", "Method Description in Chinese", item -> item.methodName));
        if (isExportOrg(orgName, "Lab Group")) {
            // The copied exporter writes Remark into these four Lab Group columns.
            columns.add(abilityExportColumn("标准编号SGS", "SGS", item -> item.remark));
            columns.add(abilityExportColumn("标准编号SOP", "SOP", item -> item.remark));
            columns.add(abilityExportColumn("标准编号OTHERS", "OTHERS", item -> item.remark));
            columns.add(abilityExportColumn("标准编号DZ", "DZ", item -> item.remark));
        }
        columns.add(abilityExportColumn("方法英文描述", "Method Description in English", item -> item.methodEngName));
        columns.add(abilityExportColumn("检测周期/工作日", "TAT/Working Day", item -> item.cycleWorkingDay));
        columns.add(abilityExportColumn("所需样品量", "Required Sample Weight/g", item -> item.massRequired));
        columns.add(abilityExportColumn("样品粒度要求", "Size Requirement/mm", item -> item.sizeRequired));
        if (labs.isEmpty()) {
            columns.add(abilityExportColumn("实验室能力", "", item -> ""));
        } else {
            for (int index = 0; index < labs.size(); index++) {
                Laboratory lab = labs.get(index);
                String title = index == 0 ? "实验室能力" : "";
                columns.add(abilityExportColumn(title, lab.code, item -> labValue(item, lab.code)));
            }
        }
        if (!isExportOrg(orgName, "NF") && !isExportOrg(orgName, "CHEM")) {
            columns.add(abilityExportColumn("备注", "Remark", item -> item.remark));
        }
        columns.add(abilityExportColumn("适用范围", "Application Scope", item -> item.detectionLimit));
        columns.add(abilityExportColumn("类型", "Type", item -> item.typeName));
        return columns;
    }

    private AbilityExportColumn abilityExportColumn(String title, String subtitle, Reader<Ability> reader) {
        return new AbilityExportColumn(title, subtitle, reader);
    }

    private boolean isExportOrg(String orgName, String expected) {
        return safe(orgName).equals(expected);
    }

    private String exportOrgName(String orgName, List<Ability> rows) {
        if (!safe(orgName).isBlank()) {
            return safe(orgName);
        }
        return rows.stream()
                .map(item -> safe(item.orgName))
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private List<Laboratory> exportedLabs(List<Ability> rows) {
        Set<String> usedCodes = new LinkedHashSet<>();
        for (Ability row : rows) {
            for (LabAbility lab : row.labAbilities) {
                if (!safe(lab.code).isBlank()) {
                    usedCodes.add(lab.code);
                }
            }
        }
        return store.labs().stream()
                .filter(lab -> usedCodes.contains(lab.code))
                .toList();
    }

    private void writeSubcontractSheet(Sheet sheet, List<SubcontractAbility> rows) {
        Row title = sheet.createRow(0);
        Row header = sheet.createRow(1);
        CellStyle headerStyle = headerStyle(sheet.getWorkbook());
        writeHeaderCell(title, 0, "分包能力", headerStyle);
        if (SUBCONTRACT_COLUMNS.size() > 1) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, SUBCONTRACT_COLUMNS.size() - 1));
        }
        for (int index = 0; index < SUBCONTRACT_COLUMNS.size(); index++) {
            writeHeaderCell(header, index, SUBCONTRACT_COLUMNS.get(index).title, headerStyle);
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            SubcontractAbility item = rows.get(rowIndex);
            Row row = sheet.createRow(rowIndex + 2);
            for (int index = 0; index < SUBCONTRACT_COLUMNS.size(); index++) {
                row.createCell(index).setCellValue(safe(SUBCONTRACT_COLUMNS.get(index).reader.read(item)));
            }
        }
        autosize(sheet, SUBCONTRACT_COLUMNS.size());
        sheet.createFreezePane(0, 2);
    }

    private ImportAbilityTableDto readAbilityRow(Row row, Map<String, Integer> headers, Long orgId, String sheetName,
                                                 Map<Integer, String> labColumns, Set<String> knownLabCodes) {
        ImportAbilityTableDto item = new ImportAbilityTableDto();
        // Column names follow the copied ability template so imports and exports round-trip.
        item.orgName = orgName(orgId);
        item.typeName = sheetName;
        item.samplingName = cell(row, headers, "样品名称");
        item.productCode = cell(row, headers, "产品代码");
        item.testItem = cell(row, headers, "测试项目");
        item.testItemRemark = cell(row, headers, "测试项目说明");
        item.standardNo = cellAny(row, headers, "标准号", "标准编号");
        item.methodName = cell(row, headers, "方法中文描述");
        item.methodRemark = cell(row, headers, "方法说明");
        item.methodEngName = cell(row, headers, "方法英文描述");
        item.gbNo = cell(row, headers, "GB标准号");
        item.gbRemark = cell(row, headers, "GB备注");
        item.isoNo = cell(row, headers, "ISO标准号");
        item.isoRemark = cell(row, headers, "ISO备注");
        item.gbtNo = cell(row, headers, "GB/T标准号");
        item.gbtRemark = cell(row, headers, "GB/T备注");
        item.astmNo = cell(row, headers, "ASTM标准号");
        item.astmRemark = cell(row, headers, "ASTM备注");
        item.industryStandardNo = cell(row, headers, "行业标准号");
        item.industryStandardRemark = cell(row, headers, "行业标准备注");
        item.otherNo = cell(row, headers, "其他编号");
        item.otherRemark = cell(row, headers, "其他编号备注");
        item.cycleWorkingDay = cell(row, headers, "检测周期/工作日");
        item.testTime = cell(row, headers, "测试时间");
        item.testTimeRemark = cell(row, headers, "测试时间备注");
        item.massRequired = cellAny(row, headers, "样品量(g)", "所需样品量");
        item.massRequiredRemark = cell(row, headers, "样品量备注");
        item.sizeRequired = cellAny(row, headers, "粒度要求", "样品粒度要求");
        item.sizeRequiredRemark = cell(row, headers, "粒度要求备注");
        item.detectionLimit = cell(row, headers, "适用范围");
        item.price = cellAny(row, headers, "价格", "价格/CNY");
        item.priceRemark = cell(row, headers, "价格备注");
        item.remark = cell(row, headers, "备注");
        item.standardNoSgs = cellAny(row, headers, "SGS标准号", "标准编号SGS");
        item.standardNoSop = cellAny(row, headers, "SOP标准号", "标准编号SOP");
        item.standardNoOthers = cellAny(row, headers, "其他标准号", "标准编号OTHERS");
        item.standardNoDz = cellAny(row, headers, "地质标准号", "标准编号DZ");
        headers.forEach((title, index) -> {
            if (title.startsWith("实验室:")) {
                item.labData.put(title.substring("实验室:".length()), text(row, index));
            }
        });
        labColumns.forEach((index, code) -> item.labData.put(code, text(row, index)));
        item.exception = requiredErrors(
                required("样品名称", item.samplingName),
                required("测试项目", item.testItem),
                required("方法中文描述", item.methodName),
                required("方法英文描述", item.methodEngName),
                required("标准编号", item.standardNo),
                required("检测周期/工作日", item.cycleWorkingDay),
                required("所需样品量", item.massRequired),
                required("样品粒度要求", item.sizeRequired),
                required("适用范围", item.detectionLimit),
                required("价格/CNY", item.price),
                validateLabData(item.labData, knownLabCodes));
        return item;
    }

    private SubcontractAbility readSubcontractRow(Row row, Map<String, Integer> headers) {
        SubcontractAbility item = new SubcontractAbility();
        item.labName = cell(row, headers, "实验室名称");
        item.contactDetails = cell(row, headers, "联系方式");
        item.testCategory = cell(row, headers, "检测/校准项目或类别");
        item.cmaOrCnas = cell(row, headers, "CMA/CNAS No(截止日期)");
        item.gist = cell(row, headers, "选定依据");
        item.appraiser = cell(row, headers, "评估人");
        item.evaluationResult = cell(row, headers, "评估结果");
        item.exception = requiredErrors(
                required("实验室名称", item.labName),
                required("检测/校准项目或类别", item.testCategory));
        return item;
    }

    private ImportUserDto readUserImportRow(Row row) {
        ImportUserDto item = new ImportUserDto();
        item.userName = text(row, 0);
        item.name = text(row, 1);
        item.surname = text(row, 2);
        item.emailAddress = text(row, 3);
        item.phoneNumber = text(row, 4);
        item.password = text(row, 5);
        item.assignedRoleNames = resolveImportRoles(text(row, 6));
        item.exception = requiredErrors(
                required("UserName", item.userName),
                required("Name", item.name),
                required("Surname", item.surname),
                required("EmailAddress", item.emailAddress),
                required("Password", item.password));
        if (!safe(item.emailAddress).isBlank() && !item.emailAddress.contains("@")) {
            item.exception = appendError(item.exception, "EmailAddress格式不正确");
        }
        if (store.userByUserName(item.userName).isPresent()) {
            item.exception = appendError(item.exception, "UserName已存在");
        }
        if (store.userByEmail(item.emailAddress).isPresent()) {
            item.exception = appendError(item.exception, "EmailAddress已存在");
        }
        if (item.assignedRoleNames.stream().anyMatch(name -> name.startsWith("!"))) {
            item.exception = appendError(item.exception,
                    "Roles不存在: " + item.assignedRoleNames.stream().filter(name -> name.startsWith("!"))
                            .map(name -> name.substring(1)).reduce((left, right) -> left + "," + right).orElse(""));
            item.assignedRoleNames = item.assignedRoleNames.stream().filter(name -> !name.startsWith("!")).toList();
        }
        return item;
    }

    private UpdateStandardNumberDto readStandardRow(Row row, Map<String, Integer> headers) {
        UpdateStandardNumberDto item = new UpdateStandardNumberDto();
        // Header names mirror the original UpdateStandardNumberDto Column attributes.
        item.old = first(cell(row, headers, "原标准号"), cell(row, headers, "旧标准号"));
        item.newValue = first(cell(row, headers, "新标准号"), cell(row, headers, "新编号"));
        item.name = cell(row, headers, "标准名称");
        item.statu = first(cell(row, headers, "标准状态"), cell(row, headers, "状态"));
        item.remark = cell(row, headers, "备注");
        item.matchedCount = store.countStandardMatches(item.old);
        return item;
    }

    private Ability toAbility(ImportAbilityTableDto row, Long orgId) {
        Ability ability = new Ability();
        OrganizationUnit org = findOrg(row.orgName, orgId);
        // Resolve the organization when possible, but keep imported text for preview data.
        ability.orgId = org == null ? orgId : Long.valueOf(org.id);
        ability.orgName = org == null ? row.orgName : org.displayName;
        ability.typeName = row.typeName;
        ability.samplingName = row.samplingName;
        ability.productCode = row.productCode;
        ability.testItem = row.testItem;
        ability.testItemRemark = row.testItemRemark;
        ability.methodName = row.methodName;
        ability.methodRemark = row.methodRemark;
        ability.methodEngName = row.methodEngName;
        ability.gbNo = row.gbNo;
        ability.gbRemark = row.gbRemark;
        ability.isoNo = row.isoNo;
        ability.isoRemark = row.isoRemark;
        ability.gbtNo = row.gbtNo;
        ability.gbtRemark = row.gbtRemark;
        ability.astmNo = row.astmNo;
        ability.astmRemark = row.astmRemark;
        ability.industryStandardNo = row.industryStandardNo;
        ability.industryStandardRemark = row.industryStandardRemark;
        ability.otherNo = row.otherNo;
        ability.otherRemark = row.otherRemark;
        ability.cycleWorkingDay = row.cycleWorkingDay;
        ability.testTime = row.testTime;
        ability.testTimeRemark = row.testTimeRemark;
        ability.massRequired = row.massRequired;
        ability.massRequiredRemark = row.massRequiredRemark;
        ability.sizeRequired = row.sizeRequired;
        ability.sizeRequiredRemark = row.sizeRequiredRemark;
        ability.detectionLimit = row.detectionLimit;
        ability.price = row.price;
        ability.priceRemark = row.priceRemark;
        ability.standardNo = row.standardNo;
        ability.remark = row.remark;
        ability.standardNoSgs = row.standardNoSgs;
        ability.standardNoSop = row.standardNoSop;
        ability.standardNoOthers = row.standardNoOthers;
        ability.standardNoDz = row.standardNoDz;
        ability.labAbilities = labAbilities(row.labData);
        return ability;
    }

    private List<LabAbility> labAbilities(Map<String, String> labData) {
        if (labData == null) {
            return new ArrayList<>();
        }
        List<LabAbility> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : labData.entrySet()) {
            String rawValue = safe(entry.getValue());
            if (rawValue.isBlank() || !isValidLabAbilityValue(rawValue)) {
                continue;
            }
            LabAbility lab = new LabAbility();
            lab.code = entry.getKey();
            lab.isAbility = labAbilityAvailable(rawValue);
            String normalized = normalizeLabValue(rawValue);
            lab.hasCma = normalized.equals("ALL") || normalized.contains("CMA");
            lab.hasCnas = normalized.equals("ALL") || normalized.contains("CNAS");
            values.add(lab);
        }
        return values;
    }

    private void summarizeAbilityUpload(AbilityTableUploadOutput output) {
        output.totalCount = output.abilityTableList.size();
        output.errorCount = (int) output.abilityTableList.stream().filter(item -> !safe(item.exception).isBlank()).count();
        output.duplicateCount = (int) output.abilityTableList.stream().filter(item -> item.isExist).count();
        output.errorFile = errorReport("能力表导入错误.xlsx", output.abilityTableList,
                abilityImportErrorColumns(output.abilityTableList),
                item -> !safe(item.exception).isBlank());
    }

    private List<Column<ImportAbilityTableDto>> abilityImportErrorColumns(List<ImportAbilityTableDto> rows) {
        List<String> labCodes = rows == null
                ? List.of()
                : rows.stream()
                .filter(Objects::nonNull)
                .flatMap(item -> item.labData == null ? java.util.stream.Stream.<String>empty() : item.labData.keySet().stream())
                .filter(value -> !safe(value).isBlank())
                .distinct()
                .toList();
        if (labCodes.isEmpty()) {
            return ABILITY_IMPORT_ERROR_COLUMNS;
        }
        List<Column<ImportAbilityTableDto>> columns = new ArrayList<>();
        columns.addAll(ABILITY_IMPORT_ERROR_COLUMNS.subList(0, ABILITY_IMPORT_ERROR_COLUMNS.size() - 1));
        for (String code : labCodes) {
            columns.add(column("实验室:" + code, item -> item.labData == null ? "" : item.labData.getOrDefault(code, "")));
        }
        columns.add(ABILITY_IMPORT_ERROR_COLUMNS.get(ABILITY_IMPORT_ERROR_COLUMNS.size() - 1));
        return columns;
    }

    private void summarizeSubcontractUpload(UploadSubcontractAbilityOutput output) {
        output.totalCount = output.items.size();
        output.errorCount = (int) output.items.stream().filter(item -> !safe(item.exception).isBlank()).count();
        output.duplicateCount = (int) output.items.stream().filter(item -> item.isExist).count();
        output.errorFile = errorReport("分包能力导入错误.xlsx", output.items, SUBCONTRACT_ERROR_COLUMNS,
                item -> !safe(item.exception).isBlank());
    }

    private void summarizeStandardUpload(UploadStandardOutput output) {
        output.totalCount = output.items == null ? 0 : output.items.size();
        output.errorCount = output.items == null
                ? 0
                : (int) output.items.stream().filter(item -> !safe(item.exception).isBlank()).count();
        output.matchedCount = output.items == null ? 0 : output.items.stream().mapToInt(item -> item.matchedCount).sum();
        output.updatedCount = output.items == null ? 0 : output.items.stream().mapToInt(item -> item.updatedCount).sum();
        output.errorFile = errorReport("标准更新导入错误.xlsx", output.items == null ? List.of() : output.items,
                STANDARD_ERROR_COLUMNS, item -> !safe(item.exception).isBlank());
    }

    private void summarizeUserImport(UserImportOutput output) {
        output.totalCount = output.items == null ? 0 : output.items.size();
        output.errorCount = output.items == null
                ? 0
                : (int) output.items.stream().filter(item -> !safe(item.exception).isBlank()).count();
        output.errorFile = errorReport("InvalidUserImportList.xlsx", "Invalid user imports",
                output.items == null ? List.of() : output.items, USER_IMPORT_ERROR_COLUMNS,
                item -> !safe(item.exception).isBlank(), 2);
    }

    private <T> FileDto errorReport(String fileName, List<T> rows, List<Column<T>> columns, Predicate<T> hasError) {
        return errorReport(fileName, "错误明细", rows, columns, hasError, 1);
    }

    private <T> FileDto errorReport(String fileName, String sheetName, List<T> rows, List<Column<T>> columns,
                                    Predicate<T> hasError, int dataStartRow) {
        List<T> errorRows = rows == null ? List.of() : rows.stream().filter(hasError).toList();
        if (errorRows.isEmpty()) {
            return null;
        }
        return tempFiles.put(fileName, EXCEL_TYPE, workbookBytes(sheetName, errorRows, columns, dataStartRow));
    }

    private Map<Integer, String> labColumns(Sheet sheet, Map<String, Integer> headers) {
        Map<Integer, String> values = new LinkedHashMap<>();
        Integer labStart = headers.get("实验室能力");
        if (labStart == null) {
            return values;
        }
        Row labRow = sheet.getRow(1);
        if (labRow == null) {
            return values;
        }
        for (int index = labStart; index < labRow.getLastCellNum(); index++) {
            String code = text(labRow, index);
            if (!code.isBlank()) {
                values.put(index, code);
            }
        }
        return values;
    }

    private Workbook open(FileDto file) throws IOException {
        return WorkbookFactory.create(new ByteArrayInputStream(tempFiles.requireContent(file)));
    }

    private Map<String, Integer> headers(Row header) {
        if (header == null) {
            throw new IllegalArgumentException("Excel 第一行必须是表头");
        }
        Map<String, Integer> values = new LinkedHashMap<>();
        for (Cell cell : header) {
            String title = text(cell);
            if (!title.isBlank()) {
                values.put(title, cell.getColumnIndex());
            }
        }
        return values;
    }

    private String cell(Row row, Map<String, Integer> headers, String title) {
        Integer index = headers.get(title);
        return index == null ? "" : text(row, index);
    }

    private String cellAny(Row row, Map<String, Integer> headers, String... titles) {
        for (String title : titles) {
            String value = cell(row, headers, title);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String required(String title, String value) {
        return safe(value).isBlank() ? title + "不能为空" : "";
    }

    private String requiredErrors(String... messages) {
        return Arrays.stream(messages).filter(message -> !message.isBlank()).reduce((left, right) -> left + "；" + right)
                .orElse("");
    }

    private String validateLabData(Map<String, String> labData, Set<String> knownLabCodes) {
        if (labData == null || labData.isEmpty()) {
            return "";
        }
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, String> entry : labData.entrySet()) {
            String code = safe(entry.getKey()).trim();
            String value = safe(entry.getValue()).trim();
            if (value.isBlank()) {
                continue;
            }
            if (code.isBlank()) {
                errors.add("实验室列名称不能为空");
                continue;
            }
            if (!knownLabCodes.contains(code)) {
                errors.add("实验室" + code + "不存在");
            }
            if (!isValidLabAbilityValue(value)) {
                errors.add("实验室" + code + "能力值无效:" + value);
            }
        }
        return requiredErrors(errors.toArray(String[]::new));
    }

    private String appendError(String current, String next) {
        return safe(current).isBlank() ? next : current + "；" + next;
    }

    private List<String> resolveImportRoles(String rawRoles) {
        if (safe(rawRoles).isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(rawRoles.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::resolveImportRole)
                .toList();
    }

    private String resolveImportRole(String value) {
        return store.roles(null).stream()
                .filter(role -> safe(role.name).equalsIgnoreCase(value) || safe(role.displayName).equalsIgnoreCase(value))
                .map(role -> role.name)
                .findFirst()
                .orElse("!" + value);
    }

    private String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
        try {
            return formatter.formatCellValue(cell, evaluator).trim();
        } catch (RuntimeException ex) {
            return formatter.formatCellValue(cell).trim();
        }
    }

    private String text(Row row, int columnIndex) {
        return text(resolvedCell(row, columnIndex));
    }

    private Cell resolvedCell(Row row, int columnIndex) {
        if (row == null || columnIndex < 0) {
            return null;
        }
        Sheet sheet = row.getSheet();
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(row.getRowNum(), columnIndex)) {
                Row firstRow = sheet.getRow(region.getFirstRow());
                return firstRow == null ? null : firstRow.getCell(region.getFirstColumn());
            }
        }
        return row.getCell(columnIndex);
    }

    private boolean rowIsBlank(Row row) {
        for (Cell cell : row) {
            if (!text(row, cell.getColumnIndex()).isBlank()) {
                return false;
            }
        }
        for (CellRangeAddress region : row.getSheet().getMergedRegions()) {
            if (region.getFirstRow() < row.getRowNum() && region.getLastRow() >= row.getRowNum()) {
                if (!text(row, region.getFirstColumn()).isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean userImportRowIsBlank(Row row) {
        return text(row, 0).isBlank();
    }

    private OrganizationUnit findOrg(String orgName, Long orgId) {
        return store.orgUnits().stream()
                .filter(org -> (orgId != null && Objects.equals(org.id, orgId)) || Objects.equals(org.displayName, orgName))
                .findFirst()
                .orElse(null);
    }

    private String orgName(Long orgId) {
        return findOrg(null, orgId) == null ? "" : findOrg(null, orgId).displayName;
    }

    private String labValue(Ability ability, String code) {
        return ability.labAbilities.stream()
                .filter(item -> Objects.equals(item.code, code))
                .findFirst()
                .map(item -> {
                    if (item.hasCma && item.hasCnas) {
                        return "CMA,CNAS";
                    }
                    if (item.hasCma) {
                        return "CMA";
                    }
                    if (item.hasCnas) {
                        return "CNAS";
                    }
                    if (item.isAbility) {
                        return "√";
                    }
                    return "";
                })
                .orElse("");
    }

    private boolean isValidLabAbilityValue(String value) {
        String normalized = normalizeLabValue(value);
        if (normalized.isBlank()) {
            return true;
        }
        if (POSITIVE_LAB_VALUES.contains(normalized) || NEGATIVE_LAB_VALUES.contains(normalized)) {
            return true;
        }
        List<String> parts = Arrays.stream(normalized.split("[/,;]"))
                .filter(part -> !part.isBlank())
                .toList();
        return !parts.isEmpty() && parts.stream().allMatch(part -> part.equals("CMA") || part.equals("CNAS"));
    }

    private boolean labAbilityAvailable(String value) {
        String normalized = normalizeLabValue(value);
        return !NEGATIVE_LAB_VALUES.contains(normalized);
    }

    private String normalizeLabValue(String value) {
        return safe(value).trim()
                .toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("，", ",")
                .replace("、", "/")
                .replace("／", "/")
                .replace("\\", "/")
                .replace("+", "/")
                .replace("；", ";");
    }

    private Set<String> knownLabCodes() {
        return new LinkedHashSet<>(store.labs().stream()
                .map(item -> safe(item.code).trim())
                .filter(value -> !value.isBlank())
                .toList());
    }

    private String first(String value, String fallback) {
        return safe(value).isBlank() ? fallback : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String joinValues(Collection<?> values) {
        return joinValues(values, ",");
    }

    private static String joinValues(Collection<?> values, String delimiter) {
        return values == null
                ? ""
                : values.stream().map(String::valueOf).reduce((left, right) -> left + delimiter + right).orElse("");
    }

    private static String boolText(boolean value) {
        return value ? "True" : "False";
    }

    private static String readStateName(int value) {
        return value == 2 ? "Read" : "Unread";
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castRows(List<?> rows, Class<T> type) {
        return rows.stream().filter(type::isInstance).map(item -> (T) item).toList();
    }

    private void autosize(Sheet sheet, int columns) {
        autosize(sheet, columns, Set.of());
    }

    private void autosize(Sheet sheet, int columns, Set<Integer> excludedColumns) {
        for (int index = 0; index < columns; index++) {
            if (excludedColumns.contains(index)) {
                continue;
            }
            sheet.autoSizeColumn(index);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        return style;
    }

    private CellStyle dateStyle(Workbook workbook, String dataFormatText) {
        CellStyle style = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat(dataFormatText));
        return style;
    }

    private Optional<LocalDateTime> parseDateTime(String value) {
        String normalized = safe(value).trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(normalized.replace(" ", "T")));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private void writeHeaderCell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static <T> Column<T> column(String title, Reader<T> reader) {
        return new Column<>(title, reader);
    }

    private record Column<T>(String title, Reader<T> reader) {
    }

    private record AbilityExportColumn(String title, String subtitle, Reader<Ability> reader) {
    }

    private record TemplateColumn(String property, String title, String englishTitle) {
    }

    private record ChatExportRow(String from, String to, String message, String readState, String creationTime) {
    }

    private interface Reader<T> {
        String read(T item);
    }
}
