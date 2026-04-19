//package my.hive_back.api.statics;
//
//import my.hive.common.dto.ResultDTO;
//import my.hive_back.module.attendance.model.vo.AttendanceRecordVO;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//
///**
// * 获取静态数据
// */
//@RestController
//@RequestMapping("/statics")
//public class StaticController {
//
//    /**
//     * 获取考勤记录
//     */
//    @RequestMapping("/attendance")
//    public ResultDTO<List<AttendanceRecordVO>> listAttendanceRecord() {
//        return ResultDTO.success(categoryService.listCategory());
//    }
//}
