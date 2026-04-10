package my.hive_back.module.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import my.hive_back.common.annotation.RequirePermission;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.exception.BusinessException;
import my.hive_back.common.utils.BarCodeUtil;
import my.hive_back.common.utils.CodeGeneratorUtil;
import my.hive_back.common.utils.RedisUtil;
import my.hive_back.module.inventory.InventoryInTypeEnum;
import my.hive_back.module.inventory.InventoryOperateTypeEnum;
import my.hive_back.module.inventory.mapper.*;
import my.hive_back.module.inventory.model.dto.InventoryInRequest;
import my.hive_back.module.inventory.model.dto.InventoryOutRequest;
import my.hive_back.module.inventory.model.entity.Cloth;
import my.hive_back.module.inventory.model.entity.ClothModelSpec;
import my.hive_back.module.inventory.model.entity.InventoryRecord;
import my.hive_back.module.inventory.model.entity.OutboundOrder;
import my.hive_back.module.inventory.model.entity.OutboundItem;
import my.hive_back.module.statics.inventory.mapper.InventoryTrendStaticsMapper;
import my.hive_back.module.price.mapper.PriceSkuMapper;
import my.hive_back.module.inventory.model.vo.ClothInfoVO;
import my.hive_back.module.statics.inventory.model.entity.InventoryTrendStatics;
import my.hive_back.module.statics.inventory.model.vo.InventoryTrendVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class InventoryService {

    @Resource
    private InventoryTrendStaticsMapper staticsMapper;
    @Resource
    private InventoryRecordMapper inventoryRecordMapper;
    @Resource
    private ClothModelSpecMapper clothModelSpecMapper;
    @Resource
    private BarCodeUtil barCodeUtil;
    @Resource
    private ClothMapper clothMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisUtil redisUtil;
    @Resource
    private OutboundOrderMapper outboundOrderMapper;
    @Resource
    private CodeGeneratorUtil codeGeneratorUtil;
    @Resource
    private OutboundItemMapper outboundItemMapper;
    @Resource
    private PriceSkuMapper priceSkuMapper;

    @Value("${redis.key-prefix.trend.today_in}")
    private String REDIS_TODAY_IN;
    @Value("${redis.key-prefix.trend.today_out}")
    private String REDIS_TODAY_OUT;

    private static final String INVENTORY_STATICS_IN_KEY_PREFIX = "inventory:in:statics:";
    private static final String INVENTORY_STATICS_OUT_KEY_PREFIX = "inventory:out:statics:";
    private static final String CLOTH_OUT_LOCK_PREFIX = "lock:cloth:out:";


    /**
     * 缂備胶鍠嶇粩鎾礂閵夈儳姘ㄩ柛蹇嬪劚瑜?
     */
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission(value = "inventory:in", message = "闁哄啰濮靛鍫ュ箼瀹ュ嫮绋婇幖瀛樻尭閻°劑宕楅妷銉ф皑")
    public ClothInfoVO inCloth(@Valid InventoryInRequest inventoryInRequest) {
        InventoryInTypeEnum inTypeEnum = InventoryInTypeEnum.getCode(inventoryInRequest.getInType());
        String barcode;

        // 1. 闁汇垻鍠愰崹姘跺级閿涘嫮鍨抽梺顐ｆ缁?
        switch (inTypeEnum) {
            case SCAN -> barcode = inventoryInRequest.getBarcode();
            case HAND, AUTO -> {
                barcode = barCodeUtil.createBarCode(TenantPermissionContext.getTenantCode());
                inventoryInRequest.setBarcode(barcode);
                inventoryHandIn(inventoryInRequest);
            }
            default -> throw new BusinessException("闁哄牜浜為悡锟犳儍閸曨偄寮抽幖瀛樻尵鐞氼偊宕?);
        }

        // 2. 鐎殿喖鍊归鐐电磼鐎涙ê袘闁搞劌顑呰ぐ璺ㄦ喆閸曨剛澹愰柨娑欎亢椤寮介悡搴ｆ皑闁圭粯甯掗崣鍡樼▔瀹ュ懎顨涢柛婵嗙С鐎靛苯霉娴ｈ　鏌ら柛蹇嬪劚缁ㄨ京绱掗幘瀵镐函
        saveClothModelSpecAsync(inventoryInRequest.getModelCode(), inventoryInRequest.getSpec(), TenantPermissionContext.getTenantCode());

        ClothInfoVO clothInfoVO = new ClothInfoVO();
        BeanUtils.copyProperties(inventoryInRequest, clothInfoVO);
        return clothInfoVO;
    }

    /**
     * 闁告垵鎼花閬嶆焻閺勫繒甯?
     */
    @RequirePermission(value = "inventory:out", message = "闁哄啰濮靛鍫ュ箼瀹ュ嫮绋婇幖瀛樻尭閻°劑宕欓崫鍕皑")
    @Transactional(rollbackFor = Exception.class)
    public ClothInfoVO outCloth(@Valid InventoryOutRequest request) {
        String barCode = request.getBarcode();
        String orderNo = request.getOrderNo();
        String customerName = request.getCustomerName();
        String tenantCode = TenantPermissionContext.getTenantCode();
        Long userId = TenantPermissionContext.getUserId();

        // 1. 闂傚啯褰冪亸浠嬪箑瑜庨ˉ鍛村蓟?
        if (barCode == null) {
            throw new BusinessException("闂傚牏鍋炵涵鍫曞级閿涘嫮鍨抽悹鍥敱閻?);
        }

        String lockKey = CLOTH_OUT_LOCK_PREFIX + tenantCode + ":" + barCode;

        // 2. 闁告帒妫楃粩宄邦嚕韫囨稒鏁氶柨娑欏哺濡茶顫㈤姀銏㈠彋闁哄啫鐖煎Λ鍧楀礃閸涱収鍤犻柛姘缁旀挳寮堕敍鍕灣闁汇劌瀚伴悵顕€鐛捄鍝勭岛闁告劖褰冮崵?
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("閻犲洢鍎辩粩鐑藉础闁垮鍔€闁革负鍔岄ˇ鈺呮偠閸℃洝鍘柨娑樼焷椤曨剛绮欏鍛€?);
        }

        try {
            // 3. 闁哄被鍎撮妤呭储閻旈顏撮柛鏍偓娑楃箚闁?
            Cloth cloth = selectClothByBarCode(barCode);
            if (cloth == null) {
                throw new BusinessException("闁哄绱曢悥婊勭▔瀹ュ懐鎽犻柛?);
            }

            Float metersToOut = request.getMeters();

            // 3.1 闁圭瑳鍡╂斀闁告垵鎼花閬嶅箥閿濆懎娅?(濞ｅ洦绻冪€垫梹鎷呴悩鎻掓枾闁哄牆顦卞▓鎴炲濡⒈娼婇梺顐ｆ缁额偅绋夊鍛秮)
            if (metersToOut != null && metersToOut > 0) {
                LambdaUpdateWrapper<Cloth> luw = new LambdaUpdateWrapper<>();
                luw.eq(Cloth::getBarcode, barCode)
                        .eq(Cloth::getTenantCode, tenantCode)
                        .ge(Cloth::getRemainingMeters, metersToOut)
                        .setSql("remaining_meters = remaining_meters - " + metersToOut)
                        .set(Cloth::getOutTime, LocalDateTime.now())
                        .set(Cloth::getOutOperatorId, userId)
                        .setSql("status = CASE WHEN remaining_meters = " + metersToOut + " THEN " + InventoryOperateTypeEnum.OUT.getCode() +
                                " ELSE " + InventoryOperateTypeEnum.PART_OUT.getCode() + " END");

                int rows = clothMapper.update(luw);
                if (rows == 0) {
                    throw new BusinessException("闁告垵鎼花杈ㄥ緞鏉堫偉袝闁挎稒鑹剧花杈┾偓娑櫭肩粭澶屾惥閾忣偄鐏楅柣妯垮煐閳ь兛绀佺槐鎾舵暜?);
                }
                cloth.setRemainingMeters(cloth.getRemainingMeters() - metersToOut);
            } else {
                metersToOut = cloth.getRemainingMeters();
                LambdaUpdateWrapper<Cloth> luw = new LambdaUpdateWrapper<>();
                luw.eq(Cloth::getBarcode, barCode)
                        .eq(Cloth::getTenantCode, tenantCode)
                        .set(Cloth::getRemainingMeters, 0)
                        .set(Cloth::getStatus, InventoryOperateTypeEnum.OUT.getCode())
                        .set(Cloth::getOutTime, LocalDateTime.now())
                        .set(Cloth::getOutOperatorId, userId);
                clothMapper.update(luw);
                cloth.setRemainingMeters(0F);
            }

            // ==================== 闁靛棙鍔栭弻濠冩櫠閻戝洨绐楅柛鎴濇惈缁ㄩ亶宕￠弴鐔风ウ鐟滅増甯″▔锕傛焻閺勫繒甯嗛柕?====================
            // 闁搞儳濮崇拹鐔煎捶閵婏富鍔冨璺哄缁楀倿寮悷鏉垮殥缂備礁绻戞晶鎼佸礄韫囨柨鐏囬柛鏃傚櫐缁辨繃绋夐弬娆炬П濞?@Transactional 闁告劕鎷戠槐婵嬫嚐閵夈倗鐟撻柡鍌濐潐婵倝鏌ㄥ▎娆戠濞戞挸锕浼存儍閸曨偆姘ㄩ悗娑櫳戞晶鎼佸礄韫囧海绐楅悗鐟邦槸閸欏繘宕堕悙瀵告硦闁?

            // a. 闁哄被鍎叉竟妯裤亹閹惧啿顤呯紒澶屽枑閸╂稒绋夌€ｅ墎绀夐悹鍥ュ劚椤撳綊骞嬮柨瀣﹂柛姘剧畱閻°劑宕烽妸鈶╁亾濠婂啰绐￠柟鍨尭瀹?(0)闁炽儲绻勫▓鎴﹀触閸繆瀚欓柛鎴濇惈缁ㄩ亶宕?
            LambdaQueryWrapper<OutboundOrder> orderQuery = new LambdaQueryWrapper<>();
            orderQuery.eq(OutboundOrder::getTenantCode, tenantCode)
                    .eq(OutboundOrder::getOrderNo, orderNo)
                    .eq(OutboundOrder::getPrintStatus, 0) // 0-鐎垫澘鎳忔晶锕傚础?
                    .last("LIMIT 1"); // 濞ｅ洦绻嗛惁澶愮嵁鐠哄搫绲哄☉鎾愁儏瑜把囧蓟閵夈倗顏遍柡?

            OutboundOrder order = outboundOrderMapper.selectOne(orderQuery);

            // b. 濠碘€冲€归悘澶娾柦閳╁啯绠掔€垫澘鎳忔晶锕傚础閺夊灝绀嬮柟璇″櫙缁辨繄鎷犵€涙ɑ顫栭柡鍕靛灟缁牗寰勯埡鍌氼棁闁汇劌瀚姘扁偓骞垮灪閸╂盯鎯冮崟顓у剳濞戞挴鍋撻柛妤€鍢茬粩鐑芥晬鐏炵偓鐓€鐎点倕鎼畷鐔煎箲椤旇鐦滈悶?
            if (order == null) {
                order = new OutboundOrder();
                order.setTenantCode(tenantCode);
                order.setOrderNo(codeGeneratorUtil.generateOutboundOrderNo()); // 闁汇垻鍠愰崹姘辨嫚缁嬫娲?CK20260407001 闁汇劌瀚畷鐔煎矗?
                order.setCustomerName(customerName);
                order.setOrderStatus(0);
                order.setPrintStatus(0); // 缂傚喚鍠曠拹鐔奉嚗閸涱喖鈪甸柛妤€搴滅槐婵堢驳婢跺﹦绐?Vue PC缂佹棏鍨辨刊娲矗?
                order.setOperatorId(userId);
                order.setCreateTime(LocalDateTime.now());
                outboundOrderMapper.insert(order); // MyBatis-Plus 濞村吋淇洪崵婊堝礉閵娿儲绀€濠?ID
            }

            // c. 闁哄啰濮鹃鎴炵▔閺勫繈鈧啴寮伴娑欑厐闁哄被鍎遍崵顓㈡儍閸曨喚绠烽柡鍕靛灡閺屽﹤顕欓搹瑙勭暠闁挎稑鐭傞崗妯间焊閸℃ɑ鎷辨繛鍡忓墲婢瑰倿鎯嶆担椋庣▕濞戞捁銆€閳ь剚绮嶅Σ鎴犵磼閸″繆鍋撳┑鍥х槸閺夌偛鈧喓绠婚柛?
            OutboundItem item = new OutboundItem();
            item.setTenantCode(tenantCode);
            item.setOrderId(order.getId());
            item.setBarcode(cloth.getBarcode());
            item.setModelCode(cloth.getModelCode());
            item.setSpec(cloth.getSpec());
            // 婵炲鍔嶉崜鐗堟交濞嗘挸娅￠柡鍕靛灡濠€鏉库枎閳ュ啿姣夐幖瀛樻尵濞堟垹鐚鹃搹顐ｆ(metersToOut)闁挎稑鐭侀埀顒€濂旂粭澶愬及椤栨氨顏撮柛鏍ф贡濞堟垿宕滈埡鈧紞鎴犵尵閾忣偅娈?
            item.setMeters(metersToOut);

            // d. 閻犱緤绱曢悾缁樼闁垮澹愰柨娑樻恭C缂佹棏鍨辨晶锕傚础閺夊灝姣夐幖瀛樻尭瀹曠喖妫侀埀顒傛啺娴ｈ鈻旂紒鈧ú顏勬濡増绻愮槐?
            // 鐎点倝缂氶鍛存晬濮橆剦娲ら柡瀣矆缂嶆﹢寮垫径濠傜闁绘瑯鍓涘▓鎴﹀垂鐎ｎ亜濞囬悶娑辩厜缁辨繄鎷犻柨瀣闁?cloth.getModelCode() 闁告绮悡锛勬嫚閵忥綆鍟庨悗瑙勬皑濞堟垿宕￠弴姘卞箚
            BigDecimal price = priceSkuMapper.getPrice(tenantCode, cloth.getModelCode());
            item.setPrice(price);
            // 閻犱緤绱曢悾濠氭煂閹达富鏉洪柨娑欒壘瀹曠喐绂?* 缂侇偉娅曢弳?(閻?Float 閺夌儐鍏涚拹?BigDecimal 閻犱緤绱曢悾濠氭焼閸喖甯崇紒顔藉劤鐎硅櫕绋夐姀鐘杭)
            item.setTotalAmount(price.multiply(BigDecimal.valueOf(metersToOut)));

            outboundItemMapper.insert(item);
            // ===================================================================

            // 4. 闁告瑦鍨块埀顑跨缁辨挸顫㈤妷鈹惧亾濮樿京鍙€闁挎稒淇洪鍥亹閺囩喓銈︽慨妯肩節缁楀瞼绱掗悢娲诲悁闁挎稑鐗呯粭澶愭⒓鐠囧樊鏁氬☉鎾诡唺缁ㄣ劑宕濋埄鍐ㄧ倒濞存嚎鍊х槐?
            asyncLogAndStatics(barCode, tenantCode, userId, metersToOut, INVENTORY_STATICS_OUT_KEY_PREFIX);

            // 5. 闁告垵鎼花閬嶅箣閹邦剙顫犻柛姘嚱缁辨繄绱掗崟顕呮閻庣懓鏈弳锝夋儍閸曨偄姣夐幖瀛樻尫娣囧﹪骞侀婵堢闁搞儳鍋熺划浼村礈瀹ュ浂浼傞柟鍨尭瀹撳啴鏁嶉崼鐔告殘闁规澘绻楃换鏍煂瀹€鍐闁搞儳鍋熷▓鎴炵瀹ュ棙笑閻㈩垰鍟亸顕€骞欏鍕▕闁告艾娴峰▓鎴炴媴濞嗘挸娅ゅǎ鍥ｅ墲娴煎懘鏁?
            ClothInfoVO clothInfoVO = new ClothInfoVO();
            BeanUtils.copyProperties(cloth, clothInfoVO);
            clothInfoVO.setMeters(cloth.getRemainingMeters());

            return clothInfoVO;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 鐎殿喖鍊归鐐垫媼閺夎法绉挎繛缈犵劍閹稿绋夋惔锝囧煚閻犱讲妲勭槐鐗堝緞瑜嶇粻娆愭償閿旇棄绲归柛妤€娲︾敮鎾矗閿濆懏鎯欓幖瀛樻閳ь剛鍠庣€?
     */
    @Async
    public void asyncLogAndStatics(String barCode, String tenantCode, Long userId, Float meters, String staticsPrefix) {
        try {
            Cloth cloth = clothMapper.selectOne(new LambdaQueryWrapper<Cloth>().eq(Cloth::getBarcode, barCode).eq(Cloth::getTenantCode, tenantCode));
            if (cloth == null) return;

            // 閻犱焦婢樼紞宥吤规担瑙勫瘻
            InventoryRecord record = new InventoryRecord();
            record.setTenantCode(tenantCode);
            record.setClothId(cloth.getId());
            record.setOperatorId(userId);
            record.setOperateType(InventoryOperateTypeEnum.OUT.getCode());
            record.setOperateMeters(meters);
            record.setRemainingMeters(cloth.getRemainingMeters());
            inventoryRecordMapper.insert(record);

            // Redis 缂侀硸鍨版慨鐐电磼閻旀椿鍚€
            String key = staticsPrefix + tenantCode + ":" + LocalDate.now();
            stringRedisTemplate.opsForValue().increment(key, meters.doubleValue());
            stringRedisTemplate.expire(key, redisUtil.getSecondsToNextDay(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("鐎殿喖鍊归鐐垫媼閺夎法绉块柛鎴濇惈缁ㄨ京绱掗悢娲诲悁濠㈡儼绮剧憴? barcode: {}", barCode, e);
        }
    }

    /**
     * 鐎殿喖鍊归鐐寸┍濠靛棛鎽犻悷娆忓閻楁悂鏁嶅鑸敌╂慨婵愭線鐎靛矂宕楅妷銉ф皑婵炵繝鑳堕埢濂稿炊閻樹警娼愰柡宥囧帶缁ㄨ京浜歌箛鏃戞搐闁绘粌娲︽慨銈夋煥濞嗘巻鍋撶仦鑺ョ婵?
     */
    @Async
    public void saveClothModelSpecAsync(String modelCode, Float spec, String tenantCode) {
        ClothModelSpec specEntity = new ClothModelSpec();
        specEntity.setModelCode(modelCode);
        specEntity.setSpec(spec);
        specEntity.setTenantCode(tenantCode);
        try {
            clothModelSpecMapper.insert(specEntity);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private void inventoryHandIn(InventoryInRequest inventoryInRequest) {
        Cloth cloth = new Cloth();
        BeanUtils.copyProperties(inventoryInRequest, cloth);
        cloth.setInOperatorId(TenantPermissionContext.getUserId());
        cloth.setInTime(LocalDateTime.now());
        cloth.setTotalMeters(inventoryInRequest.getMeters());
        cloth.setRemainingMeters(inventoryInRequest.getMeters());
        cloth.setStatus(InventoryOperateTypeEnum.IN.getCode());
        cloth.setTenantCode(TenantPermissionContext.getTenantCode());

        clothMapper.insert(cloth);

        // 闁稿繈鍎辩花鍗灻规担瑙勫瘻閻犱焦婢樼紞宥夋晬閸喐鐣柡鍫簻缁辨挸顫㈤妷顖滅闁稿繈鍎辩花杈紣閹寸姴鑺抽梺顐ｈ壘閻栬埖鎷呮惔婵堣壘闁告垵鎼花?闁哄被鍎撮妤呮晬?
        InventoryRecord record = new InventoryRecord();
        record.setClothId(cloth.getId());
        record.setModelCode(inventoryInRequest.getModelCode());
        record.setTenantCode(TenantPermissionContext.getTenantCode());
        record.setOperatorId(TenantPermissionContext.getUserId());
        record.setOperateType(InventoryOperateTypeEnum.IN.getCode());
        record.setOperateMeters(inventoryInRequest.getMeters());
        record.setRemainingMeters(inventoryInRequest.getMeters());
        inventoryRecordMapper.insert(record);

        // Redis 缂備胶鍠曢鎼佸储閻旈鎽嶇紒槌栧灠婵?
        String key = INVENTORY_STATICS_IN_KEY_PREFIX + TenantPermissionContext.getTenantCode() + ":" + LocalDate.now();
        stringRedisTemplate.opsForValue().increment(key, inventoryInRequest.getMeters().doubleValue());
        stringRedisTemplate.expire(key, redisUtil.getSecondsToNextDay(), TimeUnit.SECONDS);
    }

    public Cloth selectClothByBarCode(String barCode) {
        return clothMapper.selectOne(new LambdaQueryWrapper<Cloth>()
                .eq(Cloth::getBarcode, barCode)
                .eq(Cloth::getTenantCode, TenantPermissionContext.getTenantCode()));
    }

    public List<ClothModelSpec> searchModelSpec(String keyword) {
        LambdaQueryWrapper<ClothModelSpec> queryWrapper = new LambdaQueryWrapper<>();
        if (!StringUtils.isBlank(keyword)) {
            queryWrapper.like(ClothModelSpec::getModelCode, keyword);
        }

        return clothModelSpecMapper.selectList(queryWrapper);
    }

    public List<InventoryRecord> getUserRecentRecord() {
        Long userId = TenantPermissionContext.getUserId();
        Page<InventoryRecord> page = new Page<>(1, 7);
        page.setSearchCount(false);

        LambdaQueryWrapper<InventoryRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(InventoryRecord::getOperatorId, userId)
                .orderByDesc(InventoryRecord::getCreateTime);

        return inventoryRecordMapper.selectPage(page, queryWrapper).getRecords();
    }

    public InventoryTrendVO getLastWeekTrend() {
        InventoryTrendVO vo = new InventoryTrendVO();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        // 1. 闁稿繐鐗婇悡锟犲Υ閹邦剙顤?濠㈠灈鏂傞埀顒佸灦閺嗙喖骞戦鍡欑濞寸姴椤禕闁?
        LocalDateTime dbEndDate = now.minusDays(1);  // 闁规惌浜濋娑㈠礆閻楀牊袨濠?
        LocalDateTime dbStartDate = now.minusDays(6); // 鐎垫壋鍋撻柛?濠?
        LambdaQueryWrapper<InventoryTrendStatics> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(InventoryTrendStatics::getStatDate, dbStartDate, dbEndDate);
        wrapper.orderByAsc(InventoryTrendStatics::getStatDate);
        List<InventoryTrendStatics> dbList = staticsMapper.selectList(wrapper);

        // 2. 闁哄被鍎埀顒佸姃缁牗寰勯埞搴撳亾閹存繄鏉介柡鍐煐閺嗙喖骞戦鍡欑濞寸姴鍐籩dis闁?
        Float todayIn = redisUtil.getHashValue(REDIS_TODAY_IN, TenantPermissionContext.getTenantCode(), Float.class);
        Float todayOut = redisUtil.getHashValue(REDIS_TODAY_OUT, TenantPermissionContext.getTenantCode(), Float.class);

        // 3. 缂備礁瀚ˉ?7 濠㈠灈鏅滈弳鐔煎箲椤曞棛绀勯柤濂変簻婵晝鎮?闁?
        List<String> dateList = new ArrayList<>();
        List<Float> inList = new ArrayList<>();
        List<Float> outList = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDateTime date = now.minusDays(i);
            LocalDate currentDay = date.toLocalDate();
            String dateStr = date.format(formatter);
            dateList.add(dateStr);

            if (i == 0) {
                // ======================
                // 濞寸姴锕ら妵?闁?濞?Redis 闁?
                // ======================
                inList.add(todayIn);
                outList.add(todayOut);
            } else {
                // ======================
                // 闁?濠?闁?濞?DB 闁?
                // ======================
                InventoryTrendStatics stat = dbList.stream()
                        .filter(item -> item.getStatDate().toLocalDate().equals(currentDay))
                        .findFirst().orElse(null);

                inList.add(stat == null ? 0f : stat.getDayInMeters());
                outList.add(stat == null ? 0f : stat.getDayOutMeters());
            }
        }

        vo.setDates(dateList);
        vo.setInMeters(inList);
        vo.setOutMeters(outList);
        return vo;
    }

    public void finishOutbound(String orderNo) {
        submitOutboundToPrint(orderNo);
    }

    public void submitOutboundToPrint(String orderNo) {
        String tenantCode = TenantPermissionContext.getTenantCode();

        LambdaUpdateWrapper<OutboundOrder> luw = new LambdaUpdateWrapper<>();
        luw.eq(OutboundOrder::getTenantCode, tenantCode)
                .eq(OutboundOrder::getOrderNo, orderNo)
                .eq(OutboundOrder::getPrintStatus, 0)
                .in(OutboundOrder::getOrderStatus, 0, 1)
                .set(OutboundOrder::getOrderStatus, 1)
                .set(OutboundOrder::getUpdateTime, LocalDateTime.now());

        int rows = outboundOrderMapper.update(null, luw);
        if (rows == 0) {
            throw new BusinessException("出库单不存在或已打印");
        }
    }
}