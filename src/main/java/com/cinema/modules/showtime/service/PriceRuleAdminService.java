package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.VndFormatter;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.PriceChangeAuditor;
import com.cinema.modules.showtime.dto.request.PriceRuleRequest;
import com.cinema.modules.showtime.dto.response.PriceRuleResponse;
import com.cinema.modules.showtime.entity.PriceRule;
import com.cinema.modules.showtime.enums.PriceDayType;
import com.cinema.modules.showtime.repository.PriceRuleRepository;
import com.cinema.modules.showtime.service.interfaces.IPriceRuleAdminService;
import com.cinema.modules.showtime.util.PriceRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceRuleAdminService implements IPriceRuleAdminService {

    private static final String INVALID_RULE_ERROR_CODE = "INVALID_PRICE_RULE";

    private final PriceRuleRepository priceRuleRepository;
    private final Clock clock;
    private final PriceChangeAuditor priceChangeAuditor;
    @Transactional(readOnly = true)
    @Override
    public List<PriceRuleResponse> listPriceRules(boolean deleted) {
        return priceRuleRepository.findByDeletedOrderByRuleNameAsc(deleted).stream()
                .map(PriceRuleMapper::toPriceRuleResponse)
                .toList();
    }

    @Transactional
    @Override
    public PriceRuleResponse createPriceRule(PriceRuleRequest priceRuleRequest) {
        String ruleName = priceRuleRequest.ruleName().trim();
        if (priceRuleRepository.existsByRuleNameIgnoreCaseAndDeletedFalse(ruleName)) {
            throw nameAlreadyUsed();
        }
        PriceRule priceRule = new PriceRule();
        applyRequest(priceRule, priceRuleRequest, ruleName);
        PriceRuleResponse priceRuleResponse = savePriceRule(priceRule);
        priceChangeAuditor.recordDetail(AuditAction.PRICE_RULE_CREATED, null, "PRICE_RULE", priceRuleResponse.id(),
                "Tạo quy tắc: " + describe(priceRule));
        return priceRuleResponse;
    }

    @Transactional
    @Override
    public PriceRuleResponse updatePriceRule(Long priceRuleId, PriceRuleRequest priceRuleRequest) {
        PriceRule priceRule = loadPriceRule(priceRuleId, false);
        String ruleName = priceRuleRequest.ruleName().trim();
        if (priceRuleRepository.existsByRuleNameIgnoreCaseAndDeletedFalseAndIdNot(ruleName, priceRuleId)) {
            throw nameAlreadyUsed();
        }
        String descriptionBefore = describe(priceRule);
        applyRequest(priceRule, priceRuleRequest, ruleName);
        PriceRuleResponse priceRuleResponse = savePriceRule(priceRule);
        String descriptionAfter = describe(priceRule);
        if (!descriptionBefore.equals(descriptionAfter)) {
            priceChangeAuditor.recordDetail(AuditAction.PRICE_RULE_UPDATED, null, "PRICE_RULE", priceRuleId,
                    descriptionBefore + " → " + descriptionAfter);
        }
        return priceRuleResponse;
    }

    @Transactional
    @Override
    public void deletePriceRule(Long priceRuleId) {
        PriceRule priceRule = loadPriceRule(priceRuleId, false);
        priceRule.markDeleted(Instant.now(clock));
        priceRuleRepository.save(priceRule);
        priceChangeAuditor.recordDetail(AuditAction.PRICE_RULE_DELETED, null, "PRICE_RULE", priceRuleId,
                "Xóa quy tắc: " + describe(priceRule));
    }

    @Transactional
    @Override
    public PriceRuleResponse restorePriceRule(Long priceRuleId) {
        PriceRule priceRule = loadPriceRule(priceRuleId, true);
        if (priceRuleRepository.existsByRuleNameIgnoreCaseAndDeletedFalse(priceRule.getRuleName())) {
            throw nameAlreadyUsed();
        }
        priceRule.restore();
        PriceRuleResponse priceRuleResponse = savePriceRule(priceRule);
        priceChangeAuditor.recordDetail(AuditAction.PRICE_RULE_RESTORED, null, "PRICE_RULE", priceRuleId,
                "Khôi phục quy tắc: " + describe(priceRule));
        return priceRuleResponse;
    }

    private String describe(PriceRule priceRule) {
        StringBuilder description = new StringBuilder(priceRule.getRuleName())
                .append(", phụ thu ").append(VndFormatter.format(priceRule.getSurchargeAmount()))
                .append(", loại ngày ").append(priceRule.getDayType());
        if (priceRule.getTimeFrom() != null || priceRule.getTimeTo() != null) {
            description.append(", khung giờ ").append(priceRule.getTimeFrom()).append("-").append(priceRule.getTimeTo());
        }
        if (priceRule.getScreenType() != null) {
            description.append(", màn hình ").append(priceRule.getScreenType().getCode());
        }
        if (priceRule.getValidFrom() != null || priceRule.getValidTo() != null) {
            description.append(", hiệu lực ").append(priceRule.getValidFrom()).append(" đến ").append(priceRule.getValidTo());
        }
        return description.toString();
    }

    private void applyRequest(PriceRule priceRule, PriceRuleRequest priceRuleRequest, String ruleName) {
        if (priceRuleRequest.validFrom() != null && priceRuleRequest.validTo() != null
                && priceRuleRequest.validTo().isBefore(priceRuleRequest.validFrom())) {
            throw invalidRule("Ngày kết thúc hiệu lực phải sau hoặc bằng ngày bắt đầu");
        }
        priceRule.setRuleName(ruleName);
        priceRule.setSurchargeAmount(priceRuleRequest.surchargeAmount());
        priceRule.setValidFrom(priceRuleRequest.validFrom());
        priceRule.setValidTo(priceRuleRequest.validTo());
        priceRule.setDayType(PriceDayType.ALL);
        priceRule.setTimeFrom(null);
        priceRule.setTimeTo(null);
        priceRule.setScreenType(null);
        switch (priceRuleRequest.ruleType()) {
            case DAY_TYPE -> applyDayType(priceRule, priceRuleRequest);
            case TIME_SLOT -> applyTimeSlot(priceRule, priceRuleRequest);
            case SCREEN_TYPE -> applyScreenType(priceRule, priceRuleRequest);
        }
    }

    private void applyDayType(PriceRule priceRule, PriceRuleRequest priceRuleRequest) {
        PriceDayType priceDayType = priceRuleRequest.dayType();
        if (priceDayType == null || priceDayType == PriceDayType.ALL) {
            throw invalidRule("Vui lòng chọn loại ngày: ngày thường, cuối tuần hoặc ngày lễ");
        }
        if (priceDayType == PriceDayType.HOLIDAY
                && (priceRuleRequest.validFrom() == null || priceRuleRequest.validTo() == null)) {
            throw invalidRule("Phụ thu ngày lễ cần khoảng ngày áp dụng (từ ngày, đến ngày)");
        }
        priceRule.setDayType(priceDayType);
    }

    private void applyTimeSlot(PriceRule priceRule, PriceRuleRequest priceRuleRequest) {
        if (priceRuleRequest.timeFrom() == null || priceRuleRequest.timeTo() == null) {
            throw invalidRule("Vui lòng nhập giờ bắt đầu và giờ kết thúc của khung giờ");
        }
        if (priceRuleRequest.timeFrom().equals(priceRuleRequest.timeTo())) {
            throw invalidRule("Giờ bắt đầu và giờ kết thúc của khung giờ không được trùng nhau");
        }
        priceRule.setTimeFrom(priceRuleRequest.timeFrom());
        priceRule.setTimeTo(priceRuleRequest.timeTo());
    }

    private void applyScreenType(PriceRule priceRule, PriceRuleRequest priceRuleRequest) {
        if (priceRuleRequest.screenType() == null) {
            throw invalidRule("Vui lòng chọn loại phòng chiếu");
        }
        priceRule.setScreenType(priceRuleRequest.screenType());
    }

    private PriceRule loadPriceRule(Long priceRuleId, boolean deleted) {
        return priceRuleRepository.findById(priceRuleId)
                .filter(priceRule -> priceRule.isDeleted() == deleted)
                .orElseThrow(() -> new AppException(
                        "Không tìm thấy quy tắc giá", HttpStatus.NOT_FOUND, "PRICE_RULE_NOT_FOUND"));
    }

    private PriceRuleResponse savePriceRule(PriceRule priceRule) {
        try {
            return PriceRuleMapper.toPriceRuleResponse(priceRuleRepository.saveAndFlush(priceRule));
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw nameAlreadyUsed();
        }
    }

    private static AppException nameAlreadyUsed() {
        return new AppException("Tên quy tắc giá đã tồn tại", HttpStatus.CONFLICT, "PRICE_RULE_NAME_EXISTS");
    }

    private static AppException invalidRule(String message) {
        return new AppException(message, HttpStatus.BAD_REQUEST, INVALID_RULE_ERROR_CODE);
    }
}
