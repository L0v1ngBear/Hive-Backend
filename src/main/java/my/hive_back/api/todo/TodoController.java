package my.hive_back.api.todo;

import jakarta.annotation.Resource;
import my.hive.common.dto.PageResult;
import my.hive.common.dto.Result;
import my.hive_back.module.todo.model.dto.TodoPageRequest;
import my.hive_back.module.todo.model.vo.TodoItemVO;
import my.hive_back.module.todo.service.TodoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序待办中心控制器，提供当前用户相关待办事项列表。
 */
@RestController
@RequestMapping("/todo")
public class TodoController {

    @Resource
    private TodoService todoService;

    @GetMapping("/list")
    public Result<PageResult<TodoItemVO>> list(TodoPageRequest request) {
        return Result.success(todoService.page(request));
    }
}
