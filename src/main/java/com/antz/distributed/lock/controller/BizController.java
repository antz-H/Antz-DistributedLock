package com.antz.distributed.lock.controller;

import com.antz.distributed.lock.service.IBizService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: Antz-DistributedLock
 * @description:
 * @author: huanghuang@rewin.com.cn
 * @Create: 2019-05-12 16:34
 **/
@RestController
public class BizController {

    @Autowired
    private IBizService iBizService ;

    @GetMapping("/antz/lock/redis")
    public String biz() {
        iBizService.add() ;
        return "success" ;
    }

}
