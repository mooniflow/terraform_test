package com.kt.terraform.exception.errorcode;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    /**
     * enum 클래스 이름을 return
     *
     * @return enum 클래스 이름
     */
    String name();

    /**
     * 할당된 HttpStatus 반환
     *
     * @return HttpStatus
     */
    HttpStatus getHttpStatus();

    /**
     * 지정된 메시지 반환
     *
     * @return message
     */
    String getMessage();
}
