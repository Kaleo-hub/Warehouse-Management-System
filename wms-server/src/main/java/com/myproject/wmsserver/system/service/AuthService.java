package com.myproject.wmsserver.system.service;

import com.myproject.wmsserver.system.dto.LoginDTO;
import com.myproject.wmsserver.system.vo.LoginVO;

public interface AuthService {

    LoginVO login(LoginDTO dto);

    LoginVO me();

}
