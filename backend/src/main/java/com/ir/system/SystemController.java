package com.ir.system;

import com.ir.common.R;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final UserStore users;
    private final List<Map<String,Object>> logs=new CopyOnWriteArrayList<>();
    public SystemController(UserStore users){this.users=users;}
    @GetMapping("/user") public R<List<User>> users(){List<User>out=new ArrayList<>();for(User u:Arrays.asList(users.find("admin")))if(u!=null)out.add(u);return R.ok(out);}
    @GetMapping("/op-log/page") public R<List<Map<String,Object>>> logs(){return R.ok(new ArrayList<>(logs));}
}
